package com.spark.projectanalysis.service;

import com.spark.projectanalysis.config.CallgraphPaths;
import com.spark.projectanalysis.service.dto.GitPrepareRequest;
import com.spark.projectanalysis.service.dto.GitPrepareStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
    private final ProjectRegistry registry;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-prepare");
        t.setDaemon(true);
        return t;
    });

    /** Git 工作根目录（持久缓存，跨重启复用）。可配置 callgraph.git.work-root 覆盖。 */
    private final String workRootConfig;

    /** URL 去重索引（normalizedUrl → Job） */
    private final Map<String, Job> jobsByUrl = new ConcurrentHashMap<>();

    /** 克隆后是否自动 mvn 编译的策略：always（默认）| whitelist | never */
    private final String compilePolicy;
    /** whitelist 策略下允许自动编译的 host 列表 */
    private final List<String> compileAllowedHosts;

    /** DONE/FAILED 后 Job 在内存里保留 5 分钟，让前端刷新还能查到 */
    private static final long JOB_TTL_MS = 5 * 60 * 1000L;

    static final class Job {
        final String id = UUID.randomUUID().toString();
        volatile String status = "PENDING";
        volatile String message = "";
        volatile String step = "准备中...";
        volatile int progress = 0;
        volatile String repoUrl;
        volatile String projectPath;
        volatile String projectName;
        volatile Path dir;
        /** mvn 编译的实时输出，供前端滚动展示 */
        final JobLogBuffer compileLog = new JobLogBuffer();
        /** 按策略跳过了自动编译（项目仍会克隆并注册） */
        volatile boolean compileSkipped;
        volatile String compileSkipReason;
        long createdAt;      // 创建时间
        long doneAt;         // DONE/FAILED 时间（0 表示未结束）
    }

    public GitPrepareService(GitCloneService gitCloneService, MavenCompileService mavenCompileService,
                             JavacCompileService javacCompileService,
                             ProjectRegistry registry,
                             @Value("${callgraph.git.work-root:}") String workRootConfig,
                             @Value("${callgraph.git.compile-policy:always}") String compilePolicy,
                             @Value("${callgraph.git.compile-allowed-hosts:}") List<String> compileAllowedHosts) {
        this.gitCloneService = gitCloneService;
        this.mavenCompileService = mavenCompileService;
        this.javacCompileService = javacCompileService;
        this.registry = registry;
        this.workRootConfig = workRootConfig == null ? "" : workRootConfig.trim();
        this.compilePolicy = compilePolicy == null ? "" : compilePolicy.trim();
        this.compileAllowedHosts = compileAllowedHosts == null
                ? new ArrayList<>() : new ArrayList<>(compileAllowedHosts);
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

        // URL 去重：同一个 URL 已有在途任务（CLONING/COMPILING）或刚完成（DONE，5 分钟内），直接返回；
        // FAILED 不拦截，否则编译失败后 5 分钟内无法重试。
        final String repoUrl = req.getRepoUrl().trim();
        Job ongoingJob = jobsByUrl.get(normalizeUrl(repoUrl));
        if (ongoingJob != null && !isExpired(ongoingJob) && !"FAILED".equals(ongoingJob.status)) {
            return status(ongoingJob.id);
        }
        // 清理过期条目
        cleanupExpired();

        Job job = new Job();
        job.repoUrl = repoUrl;
        job.projectName = repoDisplayName(repoUrl);
        job.createdAt = System.currentTimeMillis();
        jobs.put(job.id, job);
        jobsByUrl.put(normalizeUrl(repoUrl), job);

        executor.submit(() -> {
            job.status = "CLONING";
            job.step = "准备克隆仓库...";
            // 注入进度回调：git 输出的 Receiving/Resolving/Writing 百分比 → 整体 0-85%
            gitCloneService.beginProgress((gitPct, desc) -> {
                job.progress = Math.min(85, (int) (gitPct * 0.85));
                job.step = desc;
            });
            Path workRoot;
            try {
                workRoot = resolveAvailableWorkRoot(job);
                if ("FAILED".equals(job.status)) {
                    return;
                }
                job.dir = gitCloneService.ensureLocal(repoUrl, req.getBranch(), req.getToken(), req.getUsername(), workRoot);
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = "拉取失败：" + e.getMessage();
                if (job.dir != null && !Files.exists(job.dir.resolve(".git"))) {
                    deleteQuietly(job.dir);
                }
                return;
            } finally {
                gitCloneService.endProgress();
            }

            job.progress = 85;
            job.step = "正在检测项目结构...";

            LocatedProject located = locateProject(job.dir, job);
            if (located == null) {
                if (job.dir != null && !Files.exists(job.dir.resolve(".git"))) {
                    deleteQuietly(job.dir);
                }
                return;
            }

            job.progress = 92;
            job.step = "正在注册项目...";

            Path compileDir = located.root;
            job.projectPath = compileDir.toString();

            // Maven 工程：克隆后立即编译，保证进入分析时 target/classes 已就绪。
            // 编译失败则不注册项目——没有产物也无法分析；已克隆的目录保留，用户修正后按同一 URL 重新导入会复用该目录。
            if (located.needMaven) {
                // 克隆不可信仓库后直接 mvn compile 等于执行 pom 里绑定的任意插件代码，
                // 因此编译行为受 callgraph.git.compile-policy 控制；跳过时项目照常克隆注册。
                String skipReason = compileSkipReason(repoUrl);
                if (skipReason != null) {
                    job.compileSkipped = true;
                    job.compileSkipReason = skipReason;
                    job.step = "已跳过自动编译";
                } else {
                    job.status = "COMPILING";
                    job.progress = 90;
                    job.step = "正在 mvn 编译（首次需下载依赖，可能较慢）...";
                    MavenCompileService.CompileResult r = mavenCompileService.compile(compileDir, job.compileLog::add);
                    if (!r.isSuccess()) {
                        job.status = "FAILED";
                        job.step = "编译失败";
                        job.message = "仓库已拉取，但 Maven 编译失败：\n" + r.getOutputTail();
                        return;
                    }
                }
            }

            job.progress = 96;
            job.step = "正在注册项目...";

            // 自动注册到项目注册表
            try {
                ProjectRegistry.RegisteredProject existing = registry.getByPath(job.projectPath);
                if (existing == null) {
                    ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
                    p.id = UUID.randomUUID().toString();
                    p.name = job.projectName;
                    p.type = "GIT";
                    p.projectPath = job.projectPath;
                    p.gitUrl = repoUrl;
                    p.gitBranch = req.getBranch() == null ? "" : req.getBranch();
                    p.createdAt = System.currentTimeMillis();
                    p.lastOpenedAt = p.createdAt;
                    registry.save(p);
                } else {
                    existing.lastOpenedAt = System.currentTimeMillis();
                    existing.lastError = null;
                    registry.save(existing);
                }
            } catch (Exception ex) {
                // 注册失败不影响任务状态
            }

            job.progress = 100;
            job.status = "DONE";
            job.step = "导入完成";
            job.message = located.needMaven
                    ? (job.compileSkipped
                        ? "仓库已克隆；" + job.compileSkipReason + "请手动编译后重新分析。"
                        : "仓库已克隆并完成 mvn 编译")
                    : "项目已克隆";
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
        s.setStep(job.step);
        s.setProgress(job.progress);
        s.setRepoUrl(job.repoUrl);
        s.setProjectPath(job.projectPath);
        s.setProjectName(job.projectName);
        s.setCompileLog(job.compileLog.snapshot());
        s.setCompileSkipped(job.compileSkipped);
        s.setCompileSkipReason(job.compileSkipReason);
        return s;
    }

    /** 查找最近一个未过期的任务：在途（非 DONE/FAILED）优先，其次是刚完成的。前端刷新页面后恢复进度条 */
    public GitPrepareStatus latest() {
        cleanupExpired();
        Job bestOngoing = null;
        Job bestFinished = null;
        long ongoingCreatedAt = 0;
        long finishedCreatedAt = 0;
        for (Job j : jobs.values()) {
            if (isExpired(j)) continue;
            boolean finished = "DONE".equals(j.status) || "FAILED".equals(j.status);
            if (finished) {
                if (j.createdAt > finishedCreatedAt) {
                    bestFinished = j;
                    finishedCreatedAt = j.createdAt;
                }
            } else {
                if (j.createdAt > ongoingCreatedAt) {
                    bestOngoing = j;
                    ongoingCreatedAt = j.createdAt;
                }
            }
        }
        Job pick = bestOngoing != null ? bestOngoing : bestFinished;
        if (pick == null) return null;
        return status(pick.id);
    }

    /** URL 归一化：trim、转小写、去 .git 后缀，确保同一仓库不同写法能匹配 */
    static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        // SSH git@host:group/repo → https://host/group/repo
        u = u.replaceFirst("^git@([^:]+):", "https://$1/");
        return u;
    }

    /**
     * 判定本次是否应跳过"克隆后自动 mvn 编译"。
     *
     * @return null 表示允许编译；非 null 为跳过原因（会回传给前端）
     */
    private String compileSkipReason(String repoUrl) {
        String policy = compilePolicy.isEmpty() ? "always" : compilePolicy.toLowerCase();
        if ("never".equals(policy)) {
            return "已配置 callgraph.git.compile-policy=never，不自动编译。";
        }
        if ("whitelist".equals(policy)) {
            String host = hostOf(repoUrl);
            // 取不到主机的（本地路径 / file://）不是远端来源，按可信处理
            if (host.isEmpty() || matchesAllowedHost(host)) {
                return null;
            }
            return "该来源（" + host + "）不在 callgraph.git.compile-allowed-hosts 白名单内，未自动编译。";
        }
        // always：保持既有行为
        return null;
    }

    /** host 是否命中白名单：精确匹配或子域名匹配 */
    private boolean matchesAllowedHost(String host) {
        for (String raw : compileAllowedHosts) {
            if (raw == null) continue;
            // 兼容 YAML 列表被整体转成 "[a, b]" 的写法
            for (String h : raw.split("[,;\\s]+")) {
                String t = h.trim().toLowerCase()
                        .replace("[", "").replace("]", "")
                        .replace("\"", "").replace("'", "");
                if (t.isEmpty()) continue;
                if (t.equals(host) || host.endsWith("." + t)) return true;
            }
        }
        return false;
    }

    /** 从仓库地址取主机名（小写，去掉 userinfo 与端口）；本地路径 / file:// 取不到时返回空串 */
    static String hostOf(String repoUrl) {
        String s = repoUrl == null ? "" : repoUrl.trim();
        if (s.isEmpty()) return "";
        // SSH 写法：git@host:group/repo
        int at = s.indexOf('@');
        int colon = s.indexOf(':');
        if (at >= 0 && colon > at && !s.contains("://")) {
            return s.substring(at + 1, colon).trim().toLowerCase();
        }
        final String scheme = "://";
        int i = s.indexOf(scheme);
        if (i < 0) return "";
        String rest = s.substring(i + scheme.length());
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        int userInfo = hostPort.lastIndexOf('@');
        if (userInfo >= 0) hostPort = hostPort.substring(userInfo + 1);
        int port = hostPort.indexOf(':');
        if (port >= 0) hostPort = hostPort.substring(0, port);
        return hostPort.trim().toLowerCase();
    }

    /** 5 分钟 TTL：超过这个时间的 DONE/FAILED job 清理掉；在途永不过期 */
    private boolean isExpired(Job j) {
        if ("DONE".equals(j.status) || "FAILED".equals(j.status)) {
            return (System.currentTimeMillis() - j.createdAt) > JOB_TTL_MS;
        }
        return false;
    }

    private void cleanupExpired() {
        for (Job j : jobs.values()) {
            if (isExpired(j)) {
                jobs.remove(j.id);
                jobsByUrl.entrySet().removeIf(e -> e.getValue() == j);
            }
        }
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
     *   2) callgraph 数据目录下的 workspaces（默认 D:\.callgraph\workspaces）；
     *   3) Windows 真实用户目录 %USERPROFILE% 下的 .callgraph\workspaces（兜底）；
     *   4) JVM user.home 下的 .callgraph\workspaces（兜底）；
     *   5) 系统临时目录下的 callgraph-workspaces（最后兜底；重启系统会清空，缓存失效）。
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

        // 2) callgraph 数据目录（默认 D:\.callgraph\workspaces）
        Path defaultWs = CallgraphPaths.workspacesDir();
        if (probeWritable(defaultWs)) {
            return defaultWs;
        }

        // 3) %USERPROFILE%（Windows 真实用户目录，最匹配服务账户）
        String profile = System.getenv("USERPROFILE");
        if (profile != null && !profile.trim().isEmpty()) {
            Path cand = Paths.get(profile, ".callgraph", "workspaces");
            if (probeWritable(cand)) {
                return cand;
            }
        }

        // 4) JVM user.home
        String home = System.getProperty("user.home");
        if (home != null && !home.trim().isEmpty() && !".".equals(home.trim())) {
            Path cand = Paths.get(home, ".callgraph", "workspaces");
            if (probeWritable(cand)) {
                return cand;
            }
        }

        // 5) 系统临时目录（保证可用；重启失效）
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
