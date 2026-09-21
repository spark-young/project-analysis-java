package com.spark.projectanalysis.web;

import com.spark.projectanalysis.engine.ClasspathResolver;
import com.spark.projectanalysis.service.AnalysisException;
import com.spark.projectanalysis.service.AnalysisCacheService;
import com.spark.projectanalysis.service.ClassMetadataService;
import com.spark.projectanalysis.service.CompileOrchestrationService;
import com.spark.projectanalysis.service.EntryListService;
import com.spark.projectanalysis.service.GitCloneService;
import com.spark.projectanalysis.service.GitRefService;
import com.spark.projectanalysis.service.ProjectRegistry;
import com.spark.projectanalysis.service.ProjectRegistry.RegisteredProject;
import com.spark.projectanalysis.service.dto.AnalysisResult;
import com.spark.projectanalysis.service.dto.CacheFileResponse;
import com.spark.projectanalysis.service.dto.EntryList;
import com.spark.projectanalysis.service.dto.EntryOkResponse;
import com.spark.projectanalysis.service.dto.GitRefs;
import com.spark.projectanalysis.service.dto.ProjectInfo;
import com.spark.projectanalysis.service.dto.RemoteStatus;
import com.spark.projectanalysis.service.dto.SingleCacheResponse;
import com.spark.projectanalysis.service.dto.SwitchRequest;
import com.spark.projectanalysis.service.dto.SwitchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 项目注册表 REST 接口。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private static final Logger log = LoggerFactory.getLogger(ProjectsController.class);

    // ============ 项目变更状态（changeStatus）—— 前端契约 ============
    // app.js 按这些字面量判断渲染，序列化进 JSON 的值必须逐字不变；
    // 统一在此持有以消除散落的字符串字面量与拼写风险（OPT-31）。
    /** 磁盘目录已被移除 */
    private static final String STATUS_MISSING = "MISSING";
    /** 无编译产物 / 源码比编译产物新，需重新编译 */
    private static final String STATUS_NEEDS_COMPILE = "NEEDS_COMPILE";
    /** 已编译但尚未分析 */
    private static final String STATUS_NEEDS_ANALYZE = "NEEDS_ANALYZE";
    /** 一切就绪 */
    private static final String STATUS_UP_TO_DATE = "UP_TO_DATE";
    /** 状态检测失败 */
    private static final String STATUS_ERROR = "ERROR";

    private final ProjectRegistry registry;
    private final ClassMetadataService classMetadataService;
    private final AnalysisCacheService cacheService;
    private final EntryListService entryListService;
    private final GitCloneService gitCloneService;
    private final GitRefService gitRefService;
    private final CompileOrchestrationService compileOrchestrationService;

    public ProjectsController(ProjectRegistry registry, ClassMetadataService classMetadataService,
                              AnalysisCacheService cacheService, EntryListService entryListService,
                              GitCloneService gitCloneService, GitRefService gitRefService,
                              CompileOrchestrationService compileOrchestrationService) {
        this.registry = registry;
        this.classMetadataService = classMetadataService;
        this.cacheService = cacheService;
        this.entryListService = entryListService;
        this.gitCloneService = gitCloneService;
        this.gitRefService = gitRefService;
        this.compileOrchestrationService = compileOrchestrationService;
    }

    /** 列出所有项目，并附加实时检测的状态 */
    @GetMapping
    public List<RegisteredProject> list() {
        List<RegisteredProject> items = registry.list();
        for (RegisteredProject p : items) {
            fillRuntimeStatus(p);
        }
        return items;
    }

    /**
     * 状态检测结果缓存：状态检测要遍历编译产物与源码树，很贵。
     * 这里做短 TTL 缓存（20s），并在打开/注册/删除项目时主动失效。
     */
    private static final long STATUS_TTL_MS = 20_000L;
    private final Map<String, CachedStatus> statusCache = new ConcurrentHashMap<>();

    /** 缓存的状态快照 */
    private static final class CachedStatus {
        final boolean existsOnDisk;
        final boolean compiled;
        final boolean analyzed;
        final String changeStatus;
        final String changeHint;
        final long expireAt;

        CachedStatus(RegisteredProject p) {
            this.existsOnDisk = p.existsOnDisk;
            this.compiled = p.compiled;
            this.analyzed = p.analyzed;
            this.changeStatus = p.changeStatus;
            this.changeHint = p.changeHint;
            this.expireAt = System.currentTimeMillis() + STATUS_TTL_MS;
        }
    }

    private void invalidateStatusCache(String projectPath) {
        if (projectPath != null) statusCache.remove(projectPath);
    }

    /** 先查缓存，未命中再实际检测 */
    private void fillRuntimeStatus(RegisteredProject p) {
        CachedStatus c = p.projectPath == null ? null : statusCache.get(p.projectPath);
        if (c != null && c.expireAt > System.currentTimeMillis()) {
            p.existsOnDisk = c.existsOnDisk;
            p.compiled = c.compiled;
            p.analyzed = c.analyzed;
            p.changeStatus = c.changeStatus;
            p.changeHint = c.changeHint;
            return;
        }
        computeRuntimeStatus(p);
        if (p.projectPath != null) statusCache.put(p.projectPath, new CachedStatus(p));
    }

    /**
     * 为每个项目填充运行时状态（@JsonIgnore 字段不持久化）。
     * 检测规则（按优先级）：
     *   1. 磁盘目录不存在    → MISSING        （用户删了项目目录）
     *   2. 无编译产物         → NEEDS_COMPILE   （首次进入 / 源码变了）
     *   3. 有编译但无分析缓存 → NEEDS_ANALYZE   （编译过但没分析过）
     *   4. 源码比 .class 新  → NEEDS_COMPILE   （源码改了没重新编译）
     *   5. 都 OK             → UP_TO_DATE      （一切就绪）
     */
    private void computeRuntimeStatus(RegisteredProject p) {
        try {
            Path root = Paths.get(p.projectPath);
            // jar 型项目（直接把 fat jar 当项目分析）也是有效的：路径是文件而非目录
            boolean isJarProject = Files.isRegularFile(root);
            p.existsOnDisk = isJarProject || Files.isDirectory(root);
            p.analyzed = cacheService.hasAnyCache(p.projectPath);

            if (!p.existsOnDisk) {
                p.changeStatus = STATUS_MISSING;
                p.changeHint = "磁盘目录已被移除，请重新导入";
                p.compiled = false;
                return;
            }

            // jar 型项目本身已是编译产物，无需再检测 target/classes
            if (isJarProject) {
                p.compiled = true;
                p.changeStatus = p.analyzed ? STATUS_UP_TO_DATE : STATUS_NEEDS_ANALYZE;
                p.changeHint = p.analyzed ? "✓ 已就绪" : "未执行过分析，进入项目后点击\"开始分析\"";
                return;
            }

            // 检查是否有编译产物
            Path artifact = compileOrchestrationService.findExistingArtifacts(root);
            p.compiled = (artifact != null);

            log.info("[状态] {}: artifactDir={}, compiled={}, analyzed={}",
                    p.name, artifact, p.compiled, p.analyzed);

            if (!p.compiled) {
                p.changeStatus = STATUS_NEEDS_COMPILE;
                p.changeHint = "GIT".equals(p.type)
                        ? "未找到编译产物，重新导入或切换版本会自动 mvn 编译"
                        : "未找到编译产物：本工具不编译本地项目，请先自行编译（如 mvn compile），或直接导入 target/classes、jar";
                return;
            }

            // 编译产物 vs 源码：对比 mtime，看源码是否比 .class 新
            long newestClassMtime = newestMtime(artifact);
            long newestSourceMtime = newestSourceMtime(root);

            log.info("[状态] {}: newestClass={}, newestSource={}, diff={}ms",
                    p.name, newestClassMtime, newestSourceMtime,
                    (newestSourceMtime - newestClassMtime));

            // 容忍阈值：5 分钟内的 mtime 差异视为同时（Windows/Git 文件系统精度问题）
            // 只有源码比 .class 晚超过 5 分钟，才认为"真的修改过没重新编译"
            if (newestSourceMtime > newestClassMtime + 5 * 60 * 1000L) {
                // 源码修改时间晚于编译产物 → 需要重新编译
                p.changeStatus = STATUS_NEEDS_COMPILE;
                p.changeHint = "GIT".equals(p.type)
                        ? "源码比编译产物新，切换版本会自动重新编译"
                        : "源码比编译产物新，请自行重新编译后再分析";
                return;
            }

            if (!p.analyzed) {
                p.changeStatus = STATUS_NEEDS_ANALYZE;
                p.changeHint = "未执行过分析，进入项目后点击\"开始分析\"";
                return;
            }

            p.changeStatus = STATUS_UP_TO_DATE;
            p.changeHint = "✓ 已就绪";

        } catch (Exception e) {
            p.changeStatus = STATUS_ERROR;
            p.changeHint = "状态检测失败: " + e.getMessage();
        }
    }

    /** 目录下最新 .class 文件的 mtime */
    private long newestMtime(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".class"))
                    .mapToLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    })
                    .max().orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 源码树遍历时跳过的目录：编译产物、版本库、依赖与 IDE 元数据 */
    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg", "target", "build", "out", "bin", "dist",
            "node_modules", ".idea", ".settings", ".gradle", ".callgraph", "logs"));

    /** 项目下最新源码文件的 mtime（.java + pom.xml + build.gradle），跳过编译产物与版本库目录 */
    private long newestSourceMtime(Path root) {
        long init = 0;
        try {
            // pom.xml / build.gradle
            for (String name : new String[]{"pom.xml", "build.gradle", "build.gradle.kts"}) {
                Path p = root.resolve(name);
                if (Files.isRegularFile(p)) {
                    init = Math.max(init, Files.getLastModifiedTime(p).toMillis());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        final long[] max = {init};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 100, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String n = file.getFileName() == null ? "" : file.getFileName().toString();
                    if (n.endsWith(".java") || n.endsWith(".kt")) {
                        max[0] = Math.max(max[0], attrs.lastModifiedTime().toMillis());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            // ignore
        }
        return max[0];
    }

    /** 注册本地项目（直接加进列表，不做编译） */
    @PostMapping("/local")
    public RegisteredProject registerLocal(@RequestBody LocalRegisterRequest req) {
        if (req.path == null || req.path.isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "请填写项目路径");
        }
        // 归一化：用户可能填的是构建产物目录（.../target 或 .../target/classes），
        // 统一存成工程根，项目名才是真实工程名，缓存也不会落在会被 mvn clean 清掉的 target 里。
        String path = req.path;
        try {
            path = ClasspathResolver.projectRootOf(Paths.get(req.path)).toString();
        } catch (InvalidPathException ignore) {
            // 路径非法时保持原样，交给下面 projectInfo 统一报 400
        }

        ProjectInfo info;
        try {
            info = classMetadataService.projectInfo(path);
        } catch (Exception e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "无法识别该路径为有效 Java 项目：" + e.getMessage());
        }
        RegisteredProject existing = registry.getByPath(path);
        if (existing != null) {
            existing.lastOpenedAt = System.currentTimeMillis();
            registry.save(existing);
            invalidateStatusCache(existing.projectPath);
            return existing;
        }
        RegisteredProject p = new RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = req.name != null && !req.name.isEmpty() ? req.name : info.getName();
        p.type = "LOCAL";
        p.projectPath = path;
        p.createdAt = System.currentTimeMillis();
        p.lastOpenedAt = p.createdAt;
        return registry.save(p);
    }

    /** 打开项目（更新 lastOpenedAt） */
    @PostMapping("/{id}/open")
    public RegisteredProject open(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        p.lastOpenedAt = System.currentTimeMillis();
        registry.save(p);
        // 进入项目时让状态缓存失效，避免卡片状态滞后
        invalidateStatusCache(p.projectPath);
        return p;
    }

    // --------------------------------------------------------------
    // Git 分支 / Tag 切换与远端更新检测
    // --------------------------------------------------------------

    /** 列出远端仓库的分支与 Tag（用于下拉框选择） */
    @GetMapping("/{id}/git/refs")
    public GitRefs gitRefs(@PathVariable String id) {
        return gitRefService.listRefs(id);
    }

    /** 检查本地当前引用是否落后于远端，并回写最近检查时间 */
    @GetMapping("/{id}/git/remote-status")
    public RemoteStatus gitRemoteStatus(@PathVariable String id) {
        return gitRefService.checkRemoteUpdate(id);
    }

    /** 异步切换到指定分支 / Tag，返回 jobId */
    @PostMapping("/{id}/git/switch")
    public SwitchStatus gitSwitch(@PathVariable String id, @RequestBody SwitchRequest req) {
        return gitRefService.startSwitch(id, req);
    }

    /** 查询切换任务进度 */
    @GetMapping("/{id}/git/switch/{jobId}")
    public SwitchStatus gitSwitchStatus(@PathVariable String id, @PathVariable String jobId) {
        return gitRefService.switchStatus(jobId);
    }

    /** 列出项目内所有缓存文件（元信息，不含内容） */
    @GetMapping("/{id}/cache")
    public List<com.spark.projectanalysis.service.dto.CacheFileInfo> listCache(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        return cacheService.listAll(p.projectPath);
    }

    /** 加载项目内最新缓存（返回完整 AnalysisResult，可为空） */
    @GetMapping("/{id}/cache/latest")
    public ResponseEntity<?> loadLatestCache(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        return cacheService.loadLatest(p.projectPath)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** 按文件名加载指定缓存批次 */
    @GetMapping("/{id}/cache/load")
    public ResponseEntity<?> loadCache(@PathVariable String id, @RequestParam String fileName) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        return cacheService.loadByFileName(p.projectPath, fileName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------
    // 简化版：Step2 清单驱动的单份缓存
    // --------------------------------------------------------------

    @PostMapping("/{id}/cache/save-single")
    public EntryOkResponse saveSingleCache(@PathVariable String id,
                                           @RequestBody Map<String, Object> body) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        Object result = body.get("result");
        // 后端自己算 hash + count，不依赖前端传（避免算法不一致）
        EntryList entryList = entryListService.load(p.projectPath);
        String hash = cacheService.entryListFingerprint(entryList.getConfirmed());
        int count = entryList.getConfirmed().size();
        cacheService.saveSingle(p.projectPath, result, hash, count);

        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(true);
        return resp;
    }

    @GetMapping("/{id}/cache/load-single")
    public SingleCacheResponse loadSingleCache(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        EntryList entryList = entryListService.load(p.projectPath);
        int currentEntryCount = entryList.getConfirmed().size();

        Map<String, Object> cached = cacheService.loadSingle(p.projectPath);
        SingleCacheResponse resp = new SingleCacheResponse();
        resp.setCurrentEntryCount(currentEntryCount);

        if (cached == null) {
            resp.setHasCache(false);
            return resp;
        }
        // 算当前清单的 hash，让前端判断是否过期
        String currentHash = cacheService.entryListFingerprint(entryList.getConfirmed());
        String cachedHash = (String) cached.get("entryListHash");
        boolean dirty = !currentHash.equals(cachedHash);

        resp.setHasCache(true);
        resp.setDirty(dirty);
        resp.setCachedEntryCount((Integer) cached.get("entryCount"));
        resp.setAnalyzedAt(((Number) cached.get("analyzedAt")).longValue());
        resp.setResult(cached.get("result"));
        return resp;
    }

    /** 按缓存文件名加载单个入口的完整分析结果（批量分析展开某入口时用） */
    @GetMapping("/{id}/cache/load-file")
    public CacheFileResponse loadCacheFile(@PathVariable String id,
                                           @RequestParam("file") String fileName) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        java.util.Optional<AnalysisResult> r = cacheService.loadByFileName(p.projectPath, fileName);
        CacheFileResponse resp = new CacheFileResponse();
        if (r.isEmpty()) {
            resp.setOk(false);
            resp.setError("缓存文件不存在或已过期: " + fileName);
            return resp;
        }
        resp.setOk(true);
        resp.setResult(r.get());
        return resp;
    }

    /** 删除项目（只从注册表移除，不删除磁盘上的工作目录） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        registry.delete(id);
        invalidateStatusCache(p.projectPath);
        return ResponseEntity.noContent().build();
    }

    /** 重命名项目 */
    @PutMapping("/{id}")
    public RegisteredProject rename(@PathVariable String id, @RequestBody RenameRequest req) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        if (req.name != null && !req.name.trim().isEmpty()) {
            p.name = req.name.trim();
        }
        return registry.save(p);
    }

    public static class LocalRegisterRequest {
        public String path;
        public String name;
    }

    public static class RenameRequest {
        public String name;
    }
}
