package com.spark.projectanalysis.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 调用本机 mvn 编译被分析项目（多模块在根目录执行一次即全部编译）。
 * 仅执行标准 compile 目标，不执行任何自定义插件/脚本。
 */
@Service
public class MavenCompileService {

    private static final long DEFAULT_TIMEOUT_MIN = 15;
    private static final int TAIL_MAX = 8000;
    /** 遇到本地仓库锁冲突后的等待时间：给对方进程留出下载完成的时间，重试可直接命中缓存 */
    private static final long LOCK_RETRY_WAIT_MS = 4000;

    public CompileResult compile(Path projectDir) {
        return compile(projectDir, null);
    }

    /** 编译并把 mvn 的实时输出逐行回调给 onLine（可为 null）；用于前端展示编译过程 */
    public CompileResult compile(Path projectDir, Consumer<String> onLine) {
        return compileWithLockRetry(projectDir, DEFAULT_TIMEOUT_MIN, TimeUnit.MINUTES, false, onLine);
    }

    public CompileResult compile(Path projectDir, long timeout, TimeUnit unit) {
        return compileWithLockRetry(projectDir, timeout, unit, false, null);
    }

    /** clean compile（强制全新编译，用于"重新分析"场景） */
    public CompileResult compileClean(Path projectDir) {
        return compileWithLockRetry(projectDir, DEFAULT_TIMEOUT_MIN, TimeUnit.MINUTES, true, null);
    }

    /**
     * 带"本地仓库锁冲突"自动重试的编译。
     * 现象：多个 Maven 进程（例如本工具与用户本机 IDEA 的 Maven daemon）并发解析依赖、下载
     * 同一 artifact 时，会产生 ".part.lock 拒绝访问(Access denied)" 错误——这是并发锁冲突，
     * 不是代码问题。本工具自身是单线程串行编译，但无法阻止用户 IDEA 同时占用同一个本地仓库。
     * 解决：失败结果若命中锁冲突特征，等待片刻（等对方下载完成）后清理残留的 .part 临时文件
     * 再重试一次——重试时往往已能直接命中本地缓存，不再需要网络。
     */
    private CompileResult compileWithLockRetry(Path projectDir, long timeout, TimeUnit unit, boolean clean,
                                               Consumer<String> onLine) {
        CompileResult first = runCompile(projectDir, timeout, unit, clean, onLine);
        if (first.isSuccess() || !looksLikeRepoLock(first.getOutputTail())) {
            return first;
        }
        notifyLine(onLine, "── 检测到本地仓库并发锁冲突，稍候自动重试 ──");
        try {
            Thread.sleep(LOCK_RETRY_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return first;
        }
        clearPartialArtifacts();
        return runCompile(projectDir, timeout, unit, clean, onLine);
    }

    private static void notifyLine(Consumer<String> onLine, String line) {
        if (onLine == null) return;
        try {
            onLine.accept(line);
        } catch (Exception ignore) {
            // 回调异常不影响编译本身
        }
    }

    /** 命中 Maven 本地仓库并发锁特征：出现 .part 文件 + 访问被拒 */
    private static boolean looksLikeRepoLock(String output) {
        if (output == null || output.isEmpty()) return false;
        return output.contains(".part.lock") && output.contains("拒绝访问");
    }

    /** 清理本地仓库根目录下残留的 .part / .part.lock 临时下载文件（仅当能定位仓库目录时）。 */
    private void clearPartialArtifacts() {
        String repo = mavenRepoPath();
        if (repo == null || repo.trim().isEmpty()) return;
        File repoDir = new File(repo);
        if (!repoDir.isDirectory()) return;
        // 避免全盘递归过深，仅清理最近一次编译涉及的时间范围内的残留
        long threshold = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        try {
            java.io.FileFilter filter = f -> {
                String n = f.getName();
                if (!(n.endsWith(".part") || n.endsWith(".part.lock"))) return false;
                return f.lastModified() >= threshold;
            };
            // 先收集再删，避免遍历中并发修改
            List<File> toDelete = new ArrayList<>();
            index(java.nio.file.Paths.get(repo), filter, toDelete, 0);
            for (File f : toDelete) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        } catch (Exception ignore) {
            // 清理失败不影响重试本身（mvn 对已存在文件会自行跳过）
        }
    }

    private static void index(Path root, java.io.FileFilter filter, List<File> out, int depth) {
        if (depth > 6) return;
        File dir = root.toFile();
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                index(c.toPath(), filter, out, depth + 1);
            } else if (filter.accept(c)) {
                out.add(c);
            }
        }
    }

    /** 单次实际执行 mvn 编译 */
    private CompileResult runCompile(Path projectDir, long timeout, TimeUnit unit, boolean clean,
                                     Consumer<String> onLine) {
        // clean=true → mvn clean compile；否则 mvn compile
        String goal = clean ? "clean" : "compile";
        List<String> cmd = new ArrayList<>(Arrays.asList(mavenCommand(), "-B", "-DskipTests", goal));
        if (clean) cmd.add("compile");  // clean compile → 两个 goal
        // 与本工具的仓库定位保持一致：显式覆盖时传给 mvn
        String repoOverride = mavenRepoPath();
        if (repoOverride != null && !repoOverride.trim().isEmpty()) {
            cmd.add(2, "-Dmaven.repo.local=" + repoOverride.trim());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        try {
            Process p = pb.start();
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append('\n');
                        notifyLine(onLine, line);
                        if (output.length() > 200_000) {
                            output.delete(0, 100_000); // 防超长输出撑爆内存
                        }
                    }
                } catch (IOException ignore) {
                    // 进程被销毁时流关闭
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = p.waitFor(timeout, unit);
            reader.join(3000);
            if (!finished) {
                p.destroyForcibly();
                return CompileResult.failure("编译超时（" + timeout + " " + unit.name().toLowerCase()
                        + "）\n" + tail(output));
            }
            return p.exitValue() == 0
                    ? CompileResult.success()
                    : CompileResult.failure(tail(output));
        } catch (IOException e) {
            return CompileResult.failure("无法启动 mvn：" + e.getMessage()
                    + "。请确认服务器已安装 Maven 并在 PATH 中配置。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompileResult.failure("编译被中断");
        }
    }

    /** 定位 Maven 本地仓库路径：优先 callgraph 显式配置，否则调用者传入；无法确定时返回 null */
    private String mavenRepoPath() {
        String repoOverride = System.getProperty("callgraph.maven.repo");
        if (repoOverride == null || repoOverride.trim().isEmpty()) {
            repoOverride = System.getenv("CALLGRAPH_M2_REPO");
        }
        return (repoOverride == null || repoOverride.trim().isEmpty()) ? null : repoOverride.trim();
    }

    private static String mavenCommand() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] candidates = win
                ? new String[]{"mvn.cmd", "mvn.bat", "mvn"}
                : new String[]{"mvn"};
        for (String c : candidates) {
            if (onPath(c)) return c;
        }
        return candidates[0]; // 找不到时交给 ProcessBuilder 报错（含明确提示）
    }

    private static boolean onPath(String command) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            String d = dir.replace("\"", "").trim();
            if (d.isEmpty()) continue;
            if (new File(d, command).exists()) return true;
        }
        return false;
    }

    private static String tail(StringBuilder output) {
        String s = output.toString();
        return s.length() <= TAIL_MAX ? s : s.substring(s.length() - TAIL_MAX);
    }

    /** 编译结果 */
    public static final class CompileResult {
        private final boolean success;
        private final String outputTail;

        private CompileResult(boolean success, String outputTail) {
            this.success = success;
            this.outputTail = outputTail == null ? "" : outputTail;
        }

        public static CompileResult success() { return new CompileResult(true, ""); }
        public static CompileResult failure(String outputTail) { return new CompileResult(false, outputTail); }

        public boolean isSuccess() { return success; }
        public String getOutputTail() { return outputTail; }
    }
}
