package com.spark.callgraph.service;

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

/**
 * 调用本机 mvn 编译被分析项目（多模块在根目录执行一次即全部编译）。
 * 仅执行标准 compile 目标，不执行任何自定义插件/脚本。
 */
@Service
public class MavenCompileService {

    private static final long DEFAULT_TIMEOUT_MIN = 15;
    private static final int TAIL_MAX = 8000;

    public CompileResult compile(Path projectDir) {
        return compile(projectDir, DEFAULT_TIMEOUT_MIN, TimeUnit.MINUTES);
    }

    public CompileResult compile(Path projectDir, long timeout, TimeUnit unit) {
        List<String> cmd = new ArrayList<>(Arrays.asList(mavenCommand(), "-B", "-DskipTests", "compile"));
        // 与本工具的仓库定位保持一致：显式覆盖时传给 mvn
        String repoOverride = System.getProperty("callgraph.maven.repo");
        if (repoOverride == null || repoOverride.trim().isEmpty()) {
            repoOverride = System.getenv("CALLGRAPH_M2_REPO");
        }
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
