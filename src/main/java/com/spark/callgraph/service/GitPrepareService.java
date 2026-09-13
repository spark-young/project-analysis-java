package com.spark.callgraph.service;

import com.spark.callgraph.service.dto.GitPrepareRequest;
import com.spark.callgraph.service.dto.GitPrepareStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Git 准备任务：克隆 → 编译（Maven / 普通 Java 源码）→ 输出可分析的项目目录。
 * 异步执行：prepare 立即返回 jobId，前端轮询 status。
 * 临时目录策略：DONE 后保留供后续扫描/分析复用；FAILED 即删；单线程执行避免并发拉取。
 */
@Service
public class GitPrepareService {

    private final GitCloneService gitCloneService;
    private final MavenCompileService mavenCompileService;
    private final JavacCompileService javacCompileService;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-prepare");
        t.setDaemon(true);
        return t;
    });

    /** Git 固定工作根目录（持久缓存，跨重启复用）。可配置 callgraph.git.work-root 覆盖。 */
    private final String workRootConfig;

    static final class Job {
        final String id = UUID.randomUUID().toString();
        volatile String status = "PENDING";
        volatile String message = "";
        volatile String projectPath;
        volatile String projectName;
        volatile Path dir;
    }

    public GitPrepareService(GitCloneService gitCloneService, MavenCompileService mavenCompileService,
                             JavacCompileService javacCompileService,
                             @Value("${callgraph.git.work-root:}") String workRootConfig) {
        this.gitCloneService = gitCloneService;
        this.mavenCompileService = mavenCompileService;
        this.javacCompileService = javacCompileService;
        this.workRootConfig = workRootConfig == null ? "" : workRootConfig.trim();
    }

    /** 工程定位结果：记录最终的分析根目录，以及它是否需要走 mvn 编译 */
    private static final class LocatedProject {
        final Path root;          // 交给下游分析的项目根目录
        final boolean needMaven;  // true=需要 mvn compile；false=已就绪（已有产物或 javac 已编好）

        LocatedProject(Path root, boolean needMaven) {
            this.root = root;
            this.needMaven = needMaven;
        }
    }

    public GitPrepareStatus prepare(GitPrepareRequest req) {
        if (req == null || req.getRepoUrl() == null || req.getRepoUrl().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "仓库地址不能为空");
        }
        Job job = new Job();
        job.projectName = repoDisplayName(req.getRepoUrl());
        jobs.put(job.id, job);

        final String repoUrl = req.getRepoUrl().trim();
        final String branch = req.getBranch();
        final String token = req.getToken();
        final String username = req.getUsername();

        executor.submit(() -> {
            job.status = "CLONING";
            // 固定目录 + 增量拉取：同一个仓库本地已有就只 pull --ff-only，无需全量 clone
            Path workRoot;
            try {
                workRoot = resolveAvailableWorkRoot(job);
                if ("FAILED".equals(job.status)) {
                    return; // resolveAvailableWorkRoot 已给出明确中文错误
                }
                job.dir = gitCloneService.ensureLocal(repoUrl, branch, token, username, workRoot);
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = "拉取失败：" + e.getMessage();
                // 只删本次 clone 的半成品目录；持久缓存目录已有内容（上一次成功拉取）不删除
                if (job.dir != null) {
                    if (!Files.exists(job.dir.resolve(".git"))) {
                        deleteQuietly(job.dir);
                    }
                }
                return;
            }

            // 目录探测：定位工程根目录（Maven / Gradle / 已有产物 / 普通源码）
            LocatedProject located = locateProject(job.dir, job);
            if (located == null) {
                // locateProject 内部已设置 FAILED + 友好提示
                // 同上去除半成品，保留上一次拉取的完整缓存
                if (job.dir != null) {
                    if (!Files.exists(job.dir.resolve(".git"))) {
                        deleteQuietly(job.dir);
                    }
                }
                return;
            }
            Path compileDir = located.root;

            if (located.needMaven) {
                job.status = "COMPILING";
                job.message = "正在执行 mvn compile（多模块项目可能需要几分钟）...";
                try {
                    MavenCompileService.CompileResult r = mavenCompileService.compile(compileDir);
                    if (!r.isSuccess()) {
                        job.status = "FAILED";
                        job.message = "编译失败：\n" + r.getOutputTail();
                        // 缓存保留，不删除仓库目录（下次分析只拉增量，重新编译）
                        return;
                    }
                } catch (Exception e) {
                    job.status = "FAILED";
                    job.message = "编译异常：" + e.getMessage();
                    return;
                }
            }

            job.projectPath = compileDir.toString();
            job.status = "DONE";
            job.message = "";
        });
        return status(job.id);
    }

    public GitPrepareStatus status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new AnalysisException(HttpStatus.NOT_FOUND, "任务不存在或已过期: " + jobId);
        }
        GitPrepareStatus s = new GitPrepareStatus();
        s.setJobId(job.id);
        s.setStatus(job.status);
        s.setMessage(job.message);
        s.setProjectPath(job.projectPath);
        s.setProjectName(job.projectName);
        return s;
    }

    /** 仓库展示名：取 URL 末段并去掉 .git 后缀 */
    static String repoDisplayName(String repoUrl) {
        String s = repoUrl == null ? "" : repoUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int cut = Math.max(Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\')), s.lastIndexOf(':'));
        String name = cut >= 0 ? s.substring(cut + 1) : s;
        return name.isEmpty() ? "git-project" : name;
    }

    /**
     * 定位 clone 下来的工程根目录，并按工程类型决定后续是否走 Maven 编译。
     * 探测顺序：
     *   1) Maven（有 pom.xml，含一层/嵌套子目录）→ 走 mvn compile
     *   2) Gradle（有 build.gradle）→ 明确提示暂不支持自动编译
     *   3) 已有编译产物（目录里含 .class / target/classes 等）→ 直接作为项目根目录，无需编译
     *   4) 普通 Java 源码工程（有 .java 但无构建工具）→ 用 javac 现场编译到 build/
     *   5) 都不是 → 明确告知仓库非 Java 项目
     *
     * @return 定位结果；无法定位时返回 null 并在 job 上设置 FAILED + 友好提示
     */
    private LocatedProject locateProject(Path root, Job job) {
        try {
            // 1) Maven：根目录或一层/嵌套子目录里有 pom.xml
            Path mavenDir = findDirWith(root, "pom.xml");
            if (mavenDir != null) {
                return new LocatedProject(mavenDir, true);
            }

            // 2) Gradle
            boolean hasGradle = false;
            try (Stream<Path> walk = Files.walk(root, 2)) {
                hasGradle = walk.anyMatch(p ->
                        p.getFileName() != null &&
                        (p.getFileName().toString().equals("build.gradle")
                                || p.getFileName().toString().equals("build.gradle.kts")));
            }
            if (hasGradle) {
                job.status = "FAILED";
                job.message = "仓库内检测到 Gradle 项目（build.gradle），但当前版本不支持 Gradle 自动编译。"
                        + "如需分析 Gradle 项目，请手动编译后用'本地路径'方式分析。";
                return null;
            }

            // 3) 已有编译产物（.class 文件）：直接作为项目根目录，无需编译
            Path artifactRoot = findExistingArtifacts(root);
            if (artifactRoot != null) {
                return new LocatedProject(artifactRoot, false);
            }

            // 4) 普通 Java 源码工程：javac 现场编译
            boolean hasJava = false;
            try (Stream<Path> walk = Files.walk(root, 100)) {
                hasJava = walk.anyMatch(p ->
                        Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"));
            }
            if (hasJava) {
                job.status = "COMPILING";
                job.message = "检测到普通 Java 源码工程（无 pom.xml/build.gradle），正在用 javac 编译...";
                Path buildRoot = root.resolve("build");
                Files.createDirectories(buildRoot);
                JavacCompileService.CompileResult r = javacCompileService.compile(root, buildRoot);
                if (!r.isSuccess()) {
                    job.status = "FAILED";
                    job.message = r.getOutputTail();
                    return null;
                }
                // 产物布局：buildRoot 下有 classes/（+ 可能 lib/），ClasspathResolver 按 classes+lib 识别
                return new LocatedProject(buildRoot, false);
            }

            // 5) 完全没 Java 项目
            job.status = "FAILED";
            job.message = "未检测到 Java 项目：仓库内既没有 pom.xml/build.gradle，也没有 .java 源码或编译产物。"
                    + "该仓库可能不包含 Java 后端代码。";
            return null;

        } catch (Exception e) {
            job.status = "FAILED";
            job.message = "探测仓库目录结构时出错：" + e.getMessage();
            return null;
        }
    }

    /** 在根目录、及递归子目录（多模块/前端后端混合仓库常见，后端可能埋较深）里找含指定配置文件的目录 */
    private static Path findDirWith(Path root, String fileName) throws IOException {
        if (Files.exists(root.resolve(fileName))) {
            return root;
        }
        // 递归遍历（限制深度，排除常见噪声目录），收集所有含该文件的目录
        final int MAX_DEPTH = 8;
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            walk.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve(fileName)))
                    .filter(d -> !isNoiseDir(d))
                    .forEach(candidates::add);
        }
        if (candidates.isEmpty()) return null;
        // 多个候选取层级最浅的（越是外层根 pom 越可能是聚合根）；同深度按字母序稳定
        candidates.sort(java.util.Comparator
                .comparingInt(Path::getNameCount)
                .thenComparing(Path::toString));
        return candidates.get(0);
    }

    /** 是否为需要跳过的噪声目录（非源码目录） */
    private static boolean isNoiseDir(Path dir) {
        if (dir == null) return false;
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase();
        return name.equals(".git") || name.equals(".idea") || name.equals("node_modules")
                || name.equals("target") || name.equals("build") || name.equals("dist")
                || name.equals(".gradle") || name.equals("out");
    }

    /** 判断目录树里是否已存在编译产物（.class 文件），存在则返回该根目录 */
    private static Path findExistingArtifacts(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root, 6)) {
            if (walk.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".class"))) {
                return root;
            }
        }
        return null;
    }

    /**
 * 选择一个"确实可写"的 Git 固定工作根目录（持久缓存，跨重启复用）。
 * 候选顺序：
 *   1) callgraph.git.work-root 配置（显式指定，不可写则直接报错，不静默降级）；
 *   2) Windows 真实用户目录 %USERPROFILE% 下的 .callgraph\workspaces（服务账户 sparks 的主目录通常在此，可靠可写）；
 *   3) JVM user.home 下的 .callgraph\workspaces（兜底）；
 *   4) 系统临时目录下的 callgraph-workspaces（最后兜底；重启系统会清空，缓存失效）。
 * 每个候选都做真实写探测（建目录 + 写删探针文件），避免"目录已存在但无写权限"的假成功。
 */
    private Path resolveAvailableWorkRoot(Job job) {
        // 1) 显式配置：不可写就直接报错让用户修正
        if (!workRootConfig.isEmpty()) {
            Path cfg = Paths.get(workRootConfig);
            if (probeWritable(cfg)) {
                return cfg;
            }
            job.status = "FAILED";
            job.message = "配置的 callgraph.git.work-root 不可写：" + cfg
                    + "。请检查路径与运行用户（" + osUser() + "）的写权限。";
            return null;
        }

        // 2) %USERPROFILE%（Windows 真实用户目录，最匹配服务账户）
        String profile = System.getenv("USERPROFILE");
        if (profile != null && !profile.trim().isEmpty()) {
            Path cand = Paths.get(profile, ".callgraph", "workspaces");
            if (probeWritable(cand)) {
                return cand;
            }
        }

        // 3) JVM user.home
        String home = System.getProperty("user.home");
        if (home != null && !home.trim().isEmpty() && !".".equals(home.trim())) {
            Path cand = Paths.get(home, ".callgraph", "workspaces");
            if (probeWritable(cand)) {
                return cand;
            }
        }

        // 4) 系统临时目录（保证可用；重启失效）
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.trim().isEmpty()) {
            Path cand = Paths.get(tmp, "callgraph-workspaces");
            if (probeWritable(cand)) {
                job.message = "已使用系统临时目录作为 Git 工作目录（重启后缓存会失效）。"
                        + "如需持久缓存，请配置 callgraph.git.work-root。";
                return cand;
            }
        }

        job.status = "FAILED";
        job.message = "无法确定可写的 Git 工作目录。请配置 callgraph.git.work-root 指向一个"
                + "运行用户（" + osUser() + "）有写权限的路径。";
        return null;
    }

    /** 真实写探测：确保目录存在且当前用户能在其中新建/删除文件 */
    private static boolean probeWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".probe", null);
            Files.delete(probe);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String osUser() {
        String u = System.getProperty("user.name");
        return u == null ? "未知用户" : u;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try {
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignore) {
                        // 清理失败忽略
                    }
                });
            }
        } catch (IOException ignore) {
            // 清理失败忽略
        }
    }
}
