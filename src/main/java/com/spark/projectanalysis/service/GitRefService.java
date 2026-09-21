package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.dto.GitRefs;
import com.spark.projectanalysis.service.dto.GitSwitchResult;
import com.spark.projectanalysis.service.dto.RemoteStatus;
import com.spark.projectanalysis.service.dto.SwitchRequest;
import com.spark.projectanalysis.service.dto.SwitchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Git 分支 / Tag 切换与远端更新检测。
 * <p>
 * 切换为异步任务：startSwitch 立即返回 jobId，前端轮询 status；
 * 状态机 PENDING → FETCHING → CHECKOUT → COMPILING → DONE / FAILED。
 * 单线程执行避免并发检出同一工作区；DONE/FAILED 后 Job 内存保留 5 分钟供前端刷新恢复。
 * <p>
 * 说明：注册表里 projectPath 记录的是"编译根目录"（可能位于 Git 仓库根的子目录），
 * 因此切换前需从 projectPath 向上回溯定位真正的 Git 根目录（含 .git 的目录）。
 */
@Service
public class GitRefService {

    private static final Logger log = LoggerFactory.getLogger(GitRefService.class);

    private final GitCloneService gitCloneService;
    private final MavenCompileService mavenCompileService;
    private final JavacCompileService javacCompileService;
    private final ProjectRegistry registry;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-switch");
        t.setDaemon(true);
        return t;
    });

    /** DONE/FAILED 后 Job 在内存里保留 5 分钟，让前端刷新还能查到 */
    private static final long JOB_TTL_MS = 5 * 60 * 1000L;

    /** 定位 Git 根目录时向上回溯的最大层数 */
    private static final int MAX_GIT_ROOT_DEPTH = 14;

    static final class Job {
        final String id = UUID.randomUUID().toString();
        volatile String status = "PENDING";
        volatile String message = "";
        volatile String step = "准备中...";
        volatile int progress = 0;
        volatile String projectId;
        volatile String ref;
        volatile String refType;
        volatile boolean stashed;
        volatile boolean conflict;
        /** 重新编译时 mvn 的实时输出，供前端滚动展示 */
        final JobLogBuffer compileLog = new JobLogBuffer();
        long createdAt;
        long doneAt;
    }

    public GitRefService(GitCloneService gitCloneService, MavenCompileService mavenCompileService,
                         JavacCompileService javacCompileService, ProjectRegistry registry) {
        this.gitCloneService = gitCloneService;
        this.mavenCompileService = mavenCompileService;
        this.javacCompileService = javacCompileService;
        this.registry = registry;
    }

    /** 列出远端仓库的分支与 Tag（网络失败时返回空列表，不抛异常）。 */
    public GitRefs listRefs(String projectId) {
        ProjectRegistry.RegisteredProject p = requireGitProject(projectId);
        try {
            return gitCloneService.listRefs(p.gitUrl, p.gitToken, p.gitUsername);
        } catch (Exception e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "获取远端引用失败：" + e.getMessage());
        }
    }

    /**
     * 检查本地当前引用是否落后于远端，并回写注册表（remoteUpdateStatus + lastCheckTime）。
     * 网络异常不抛出，降级为 UNKNOWN，避免阻断"进入项目"流程。
     */
    public RemoteStatus checkRemoteUpdate(String projectId) {
        ProjectRegistry.RegisteredProject p = requireGitProject(projectId);
        Path gitRoot = findGitRoot(Paths.get(p.projectPath));

        RemoteStatus rs;
        if (gitRoot == null) {
            rs = new RemoteStatus();
            rs.setStatus("UNKNOWN");
            rs.setHint("未找到 Git 仓库根目录（.git 不存在）");
            rs.setCheckedAt(System.currentTimeMillis());
        } else {
            try {
                String[] cur = detectCurrentRef(gitRoot);
                String ref = (p.currentRef != null && !p.currentRef.isEmpty()) ? p.currentRef : cur[0];
                String refType = (p.currentRefType != null && !p.currentRefType.isEmpty())
                        ? p.currentRefType : cur[1];
                // 回填当前引用，便于后续切换/检查复用
                p.currentRef = ref;
                p.currentRefType = refType;
                rs = gitCloneService.checkRemoteUpdate(gitRoot, p.gitUrl, ref, refType,
                        p.gitToken, p.gitUsername);
            } catch (Exception e) {
                rs = new RemoteStatus();
                rs.setStatus("UNKNOWN");
                rs.setHint(e.getMessage());
                rs.setCheckedAt(System.currentTimeMillis());
            }
        }

        p.remoteUpdateStatus = rs.getStatus();
        p.lastCheckTime = rs.getCheckedAt();
        registry.save(p);
        return rs;
    }

    /** 启动异步切换任务，返回 jobId 供前端轮询。 */
    public SwitchStatus startSwitch(String projectId, SwitchRequest req) {
        ProjectRegistry.RegisteredProject p = requireGitProject(projectId);
        if (req == null || req.getRef() == null || req.getRef().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "目标分支/Tag 不能为空");
        }
        String target = req.getRef().trim();
        String targetType = "TAG".equalsIgnoreCase(req.getRefType()) ? "TAG" : "BRANCH";

        cleanupExpired();
        // 同一项目已有在途切换任务 → 直接复用，避免并发检出
        for (Job j : jobs.values()) {
            if (projectId.equals(j.projectId) && !isExpired(j)
                    && !"DONE".equals(j.status) && !"FAILED".equals(j.status)) {
                return toStatus(j);
            }
        }

        Job job = new Job();
        job.projectId = projectId;
        job.ref = target;
        job.refType = targetType;
        job.createdAt = System.currentTimeMillis();
        jobs.put(job.id, job);

        executor.submit(() -> runSwitch(job, p));
        return toStatus(job);
    }

    /** 查询切换进度。 */
    public SwitchStatus switchStatus(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new AnalysisException(HttpStatus.NOT_FOUND, "任务不存在或已过期: " + jobId);
        }
        return toStatus(job);
    }

    private void runSwitch(Job job, ProjectRegistry.RegisteredProject p) {
        job.status = "FETCHING";
        job.step = "正在获取远端引用...";
        job.progress = 5;
        try {
            Path gitRoot = findGitRoot(Paths.get(p.projectPath));
            if (gitRoot == null) {
                job.status = "FAILED";
                job.message = "未找到 Git 仓库根目录（.git 不存在）：" + p.projectPath;
                return;
            }

            GitSwitchResult r = gitCloneService.switchRef(gitRoot, p.gitUrl, job.ref, job.refType,
                    p.gitToken, p.gitUsername, (gitPct, desc) -> {
                        job.progress = Math.min(70, 5 + (int) (gitPct * 0.65));
                        job.step = desc;
                    });
            job.stashed = r.isStashed();
            job.conflict = r.isConflict();

            job.status = "CHECKOUT";
            job.progress = 78;
            job.step = "检出完成，正在识别当前引用...";
            String[] cur = detectCurrentRef(gitRoot);
            p.currentRef = cur[0];
            p.currentRefType = cur[1];

            job.status = "COMPILING";
            job.progress = 82;
            job.step = "正在重新编译...";
            String err = recompile(gitRoot, job.compileLog::add);
            if (err != null) {
                job.status = "FAILED";
                job.message = "已切换但重新编译失败：\n" + err;
                return;
            }

            job.progress = 100;
            job.status = "DONE";
            job.step = "切换完成";
            job.message = buildDoneMessage(r, p.currentRef);

            // 切换后本地与远端目标引用一致
            p.remoteUpdateStatus = "UP_TO_DATE";
            p.lastCheckTime = System.currentTimeMillis();
            registry.save(p);
        } catch (Exception e) {
            job.status = "FAILED";
            job.message = "切换失败：" + e.getMessage();
        } finally {
            job.doneAt = System.currentTimeMillis();
        }
    }

    /**
     * 切换后重新编译，保证分析产物与源码一致。
     * 从 Git 根目录重新探测工程类型（注册表里的 projectPath 可能是子目录）。
     *
     * @param onLine 编译输出逐行回调（可为 null），用于前端实时展示
     * @return null 表示成功；非空为失败输出
     */
    private String recompile(Path gitRoot, Consumer<String> onLine) throws IOException {
        // 1) Maven：根目录或嵌套子目录存在 pom.xml
        Path mavenDir = findPomDir(gitRoot);
        if (mavenDir != null) {
            MavenCompileService.CompileResult r = mavenCompileService.compile(mavenDir, onLine);
            return r.isSuccess() ? null : r.getOutputTail();
        }
        // 2) 普通 Java 源码 → javac 覆盖 build/
        if (hasJavaSources(gitRoot)) {
            if (onLine != null) onLine.accept("检测到普通 Java 源码工程，正在用 javac 编译...");
            Path buildRoot = gitRoot.resolve("build");
            Files.createDirectories(buildRoot);
            JavacCompileService.CompileResult r = javacCompileService.compile(gitRoot, buildRoot);
            return r.isSuccess() ? null : r.getOutputTail();
        }
        // 3) Gradle / 仓库自带编译产物：跳过（产物随 checkout 一并更新，交由用户手动编译）
        return null;
    }

    /** 在根目录及嵌套子目录里查找含 pom.xml 的目录，取层级最浅者 */
    private static Path findPomDir(Path root) throws IOException {
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return root;
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 8)) {
            walk.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve("pom.xml")))
                    .filter(d -> !isNoiseDir(d))
                    .forEach(candidates::add);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString));
        return candidates.get(0);
    }

    /** 目录树里是否存在 .java 源码（跳过构建/配置目录） */
    private static boolean hasJavaSources(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root, 100)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".java"))
                    .anyMatch(p -> !insideNoiseDir(p, root));
        }
    }

    private static boolean insideNoiseDir(Path file, Path root) {
        for (Path parent = file.getParent(); parent != null && !parent.equals(root); parent = parent.getParent()) {
            if (isNoiseDir(parent)) return true;
        }
        return false;
    }

    private static boolean isNoiseDir(Path dir) {
        if (dir == null || dir.getFileName() == null) return false;
        String name = dir.getFileName().toString().toLowerCase();
        return name.equals(".git") || name.equals(".idea") || name.equals("node_modules")
                || name.equals("target") || name.equals("build") || name.equals("dist")
                || name.equals(".gradle") || name.equals("out");
    }

    /**
     * 从给定目录向上回溯定位 Git 根目录（含 .git 的目录）。
     * 注册表存的是编译根目录，可能是 Git 根的子目录。
     */
    static Path findGitRoot(Path start) {
        if (start == null) return null;
        Path cur = Files.isDirectory(start) ? start : start.getParent();
        for (int i = 0; i < MAX_GIT_ROOT_DEPTH && cur != null; i++) {
            if (Files.isDirectory(cur.resolve(".git"))) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * 识别当前检出引用（委托 GitCloneService，避免与 GitPrepareService 各写一套）。
     *
     * @return [refName, refType]，refType ∈ BRANCH / TAG / DETACHED / UNKNOWN
     */
    String[] detectCurrentRef(Path gitRoot) {
        return gitCloneService.currentRef(gitRoot);
    }

    /**
     * 探测并回写当前引用，供历史记录补齐（早期导入的项目没记 currentRef）。
     * 探测失败/未变化时不做任何写入；不抛异常，不阻断打开项目。
     */
    public void refreshCurrentRef(ProjectRegistry.RegisteredProject p) {
        try {
            Path gitRoot = findGitRoot(Paths.get(p.projectPath));
            if (gitRoot == null) return;
            String[] cur = detectCurrentRef(gitRoot);
            if (cur[0] == null || cur[0].isEmpty()) return;
            if (cur[0].equals(p.currentRef) && cur[1].equals(p.currentRefType)) return;
            p.currentRef = cur[0];
            p.currentRefType = cur[1];
            registry.save(p);
        } catch (Exception e) {
            log.warn("[Git] 探测当前引用失败 {}: {}", p.projectPath, e.getMessage());
        }
    }

    private ProjectRegistry.RegisteredProject requireGitProject(String projectId) {
        ProjectRegistry.RegisteredProject p = registry.get(projectId);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        if (!"GIT".equals(p.type)) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "仅 Git 项目支持分支/Tag 操作");
        }
        if (p.gitUrl == null || p.gitUrl.trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "该项目未记录 Git 仓库地址");
        }
        return p;
    }

    /** 5 分钟 TTL：超过这个时间的 DONE/FAILED job 清理掉；在途永不过期 */
    private boolean isExpired(Job j) {
        if ("DONE".equals(j.status) || "FAILED".equals(j.status)) {
            return (System.currentTimeMillis() - j.createdAt) > JOB_TTL_MS;
        }
        return false;
    }

    private void cleanupExpired() {
        jobs.values().removeIf(this::isExpired);
    }

    private static SwitchStatus toStatus(Job job) {
        SwitchStatus s = new SwitchStatus();
        s.setJobId(job.id);
        s.setStatus(job.status);
        s.setMessage(job.message);
        s.setStep(job.step);
        s.setProgress(job.progress);
        s.setRef(job.ref);
        s.setRefType(job.refType);
        s.setStashed(job.stashed);
        s.setConflict(job.conflict);
        s.setCompileLog(job.compileLog.snapshot());
        return s;
    }

    private static String buildDoneMessage(GitSwitchResult r, String currentRef) {
        StringBuilder sb = new StringBuilder("已切换到 ").append(currentRef == null ? "" : currentRef);
        if (r.isStashed()) {
            if (r.isConflict()) {
                sb.append("；本地改动已暂存，但恢复时发生冲突，改动保留在 stash 中（请手动处理）");
            } else {
                sb.append("；本地改动已自动暂存并恢复");
            }
        }
        return sb.toString();
    }

    /** 应用关闭时优雅关闭异步执行器：先 shutdown，等待收尾，超时再 shutdownNow。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
