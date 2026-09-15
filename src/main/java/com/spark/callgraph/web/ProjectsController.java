package com.spark.callgraph.web;

import com.spark.callgraph.service.AnalysisException;
import com.spark.callgraph.service.AnalysisCacheService;
import com.spark.callgraph.service.AnalysisService;
import com.spark.callgraph.service.EntryListService;
import com.spark.callgraph.service.GitCloneService;
import com.spark.callgraph.service.GitRefService;
import com.spark.callgraph.service.ProjectRegistry;
import com.spark.callgraph.service.ProjectRegistry.RegisteredProject;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.EntryList;
import com.spark.callgraph.service.dto.GitRefs;
import com.spark.callgraph.service.dto.ProjectInfo;
import com.spark.callgraph.service.dto.RemoteStatus;
import com.spark.callgraph.service.dto.SwitchRequest;
import com.spark.callgraph.service.dto.SwitchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 项目注册表 REST 接口。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private static final Logger log = LoggerFactory.getLogger(ProjectsController.class);

    private final ProjectRegistry registry;
    private final AnalysisService analysisService;
    private final AnalysisCacheService cacheService;
    private final EntryListService entryListService;
    private final GitCloneService gitCloneService;
    private final GitRefService gitRefService;

    public ProjectsController(ProjectRegistry registry, AnalysisService analysisService,
                              AnalysisCacheService cacheService, EntryListService entryListService,
                              GitCloneService gitCloneService, GitRefService gitRefService) {
        this.registry = registry;
        this.analysisService = analysisService;
        this.cacheService = cacheService;
        this.entryListService = entryListService;
        this.gitCloneService = gitCloneService;
        this.gitRefService = gitRefService;
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
     * 为每个项目填充运行时状态（@JsonIgnore 字段不持久化）。
     * 检测规则（按优先级）：
     *   1. 磁盘目录不存在    → MISSING        （用户删了项目目录）
     *   2. 无编译产物         → NEEDS_COMPILE   （首次进入 / 源码变了）
     *   3. 有编译但无分析缓存 → NEEDS_ANALYZE   （编译过但没分析过）
     *   4. 源码比 .class 新  → NEEDS_COMPILE   （源码改了没重新编译）
     *   5. 都 OK             → UP_TO_DATE      （一切就绪）
     */
    private void fillRuntimeStatus(RegisteredProject p) {
        try {
            Path root = Paths.get(p.projectPath);
            p.existsOnDisk = Files.isDirectory(root);
            p.analyzed = cacheService.hasAnyCache(p.projectPath);

            if (!p.existsOnDisk) {
                p.changeStatus = "MISSING";
                p.changeHint = "磁盘目录已被移除，请重新导入";
                p.compiled = false;
                return;
            }

            // 检查是否有编译产物
            Path artifact = findArtifactDir(root);
            p.compiled = (artifact != null);

            log.info("[状态] {}: artifactDir={}, compiled={}, analyzed={}",
                    p.name, artifact, p.compiled, p.analyzed);

            if (!p.compiled) {
                p.changeStatus = "NEEDS_COMPILE";
                p.changeHint = "未编译，进入项目后将自动编译";
                return;
            }

            // 编译产物 vs 源码：对比 mtime，看源码是否比 .class 新
            long newestClassMtime = newestMtime(artifact, ".class");
            long oldestClassMtime = oldestMtime(artifact, ".class");
            long newestSourceMtime = newestSourceMtime(root);

            log.info("[状态] {}: newestClass={}, oldestClass={}, newestSource={}, diff={}ms",
                    p.name, newestClassMtime, oldestClassMtime, newestSourceMtime,
                    (newestSourceMtime - newestClassMtime));

            // 容忍阈值：5 分钟内的 mtime 差异视为同时（Windows/Git 文件系统精度问题）
            // 只有源码比 .class 晚超过 5 分钟，才认为"真的修改过没重新编译"
            if (newestSourceMtime > newestClassMtime + 5 * 60 * 1000L) {
                // 源码修改时间晚于编译产物 → 需要重新编译
                p.changeStatus = "NEEDS_COMPILE";
                p.changeHint = "源码已修改，需重新编译（点击\"重新分析\"）";
                return;
            }

            if (oldestClassMtime == 0) {
                // 编译目录存在但里面没 .class 文件
                p.changeStatus = "NEEDS_COMPILE";
                p.changeHint = "编译产物为空，需重新编译";
                return;
            }

            if (!p.analyzed) {
                p.changeStatus = "NEEDS_ANALYZE";
                p.changeHint = "未执行过分析，进入项目后点击\"开始分析\"";
                return;
            }

            p.changeStatus = "UP_TO_DATE";
            p.changeHint = "✓ 已就绪";

        } catch (Exception e) {
            p.changeStatus = "ERROR";
            p.changeHint = "状态检测失败: " + e.getMessage();
        }
    }

    /** 查找编译产物目录：目录存在且内部有 .class 文件才认为有效 */
    private Path findArtifactDir(Path root) {
        // 优先顺序：标准 Maven/Gradle 目录 → Eclipse bin/ → 嵌套查找
        Path[] candidates = new Path[] {
                root.resolve("target").resolve("classes"),
                root.resolve("build").resolve("classes"),
                root.resolve("bin"),                           // Eclipse 默认输出
                root.resolve("build")                          // javac 输出根目录
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c) && hasClassFiles(c)) {
                return c;
            }
        }
        // 嵌套一层兜底
        try (Stream<Path> walk = Files.walk(root, 3)) {
            return walk.filter(Files::isDirectory)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.endsWith("/target/classes") || s.endsWith("/build/classes")
                                || s.endsWith("/out/production");
                    })
                    .filter(this::hasClassFiles)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 目录或子目录下是否有 .class 文件 */
    private boolean hasClassFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".class"))
                    .findFirst().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /** 目录下最新 .class 文件的 mtime */
    private long newestMtime(Path dir, String suffix) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(suffix))
                    .mapToLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    })
                    .max().orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 目录下最旧 .class 文件的 mtime（判断是否为空） */
    private long oldestMtime(Path dir, String suffix) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(suffix))
                    .mapToLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return Long.MAX_VALUE; }
                    })
                    .min().orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 项目下最新源码文件的 mtime（.java + pom.xml + build.gradle） */
    private long newestSourceMtime(Path root) {
        long max = 0;
        try {
            // pom.xml / build.gradle
            for (String name : new String[]{"pom.xml", "build.gradle", "build.gradle.kts"}) {
                Path p = root.resolve(name);
                if (Files.isRegularFile(p)) {
                    max = Math.max(max, Files.getLastModifiedTime(p).toMillis());
                }
            }
            // .java 文件（深度 100）
            try (Stream<Path> walk = Files.walk(root, 100)) {
                max = Math.max(max, walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName() == null ? "" : p.getFileName().toString();
                            return n.endsWith(".java") || n.endsWith(".kt");
                        })
                        .mapToLong(p -> {
                            try { return Files.getLastModifiedTime(p).toMillis(); }
                            catch (Exception e) { return 0L; }
                        })
                        .max().orElse(0L));
            }
        } catch (Exception e) {
            // ignore
        }
        return max;
    }

    /** 注册本地项目（直接加进列表，不做编译） */
    @PostMapping("/local")
    public RegisteredProject registerLocal(@RequestBody LocalRegisterRequest req) {
        if (req.path == null || req.path.isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "请填写项目路径");
        }
        ProjectInfo info;
        try {
            info = analysisService.projectInfo(req.path);
        } catch (Exception e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "无法识别该路径为有效 Java 项目：" + e.getMessage());
        }
        RegisteredProject existing = registry.getByPath(req.path);
        if (existing != null) {
            existing.lastOpenedAt = System.currentTimeMillis();
            registry.save(existing);
            return existing;
        }
        RegisteredProject p = new RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = req.name != null && !req.name.isEmpty() ? req.name : info.getName();
        p.type = "LOCAL";
        p.projectPath = req.path;
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
    public List<com.spark.callgraph.service.dto.CacheFileInfo> listCache(@PathVariable String id) {
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
    public Map<String, Object> saveSingleCache(@PathVariable String id,
                                                 @RequestBody Map<String, Object> body) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        Object result = body.get("result");
        // 后端自己算 hash + count，不依赖前端传（避免算法不一致）
        EntryList entryList = entryListService.load(p.projectPath);
        String hash = cacheService.entryListFingerprint(entryList.getConfirmed());
        int count = entryList.getConfirmed().size();
        cacheService.saveSingle(p.projectPath, result, hash, count);

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    @GetMapping("/{id}/cache/load-single")
    public Map<String, Object> loadSingleCache(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        EntryList entryList = entryListService.load(p.projectPath);
        int currentEntryCount = entryList.getConfirmed().size();

        Map<String, Object> cached = cacheService.loadSingle(p.projectPath);
        Map<String, Object> resp = new HashMap<>();
        resp.put("currentEntryCount", currentEntryCount);

        if (cached == null) {
            resp.put("hasCache", false);
            return resp;
        }
        // 算当前清单的 hash，让前端判断是否过期
        String currentHash = cacheService.entryListFingerprint(entryList.getConfirmed());
        String cachedHash = (String) cached.get("entryListHash");
        boolean dirty = !currentHash.equals(cachedHash);

        resp.put("hasCache", true);
        resp.put("dirty", dirty);
        resp.put("cachedEntryCount", cached.get("entryCount"));
        resp.put("analyzedAt", cached.get("analyzedAt"));
        resp.put("result", cached.get("result"));
        return resp;
    }

    /** 按缓存文件名加载单个入口的完整分析结果（批量分析展开某入口时用） */
    @GetMapping("/{id}/cache/load-file")
    public Map<String, Object> loadCacheFile(@PathVariable String id,
                                             @RequestParam("file") String fileName) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");

        java.util.Optional<AnalysisResult> r = cacheService.loadByFileName(p.projectPath, fileName);
        Map<String, Object> resp = new HashMap<>();
        if (r.isEmpty()) {
            resp.put("ok", false);
            resp.put("error", "缓存文件不存在或已过期: " + fileName);
            return resp;
        }
        resp.put("ok", true);
        resp.put("result", r.get());
        return resp;
    }

    /** 删除项目（只从注册表移除，不删除磁盘上的工作目录） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (registry.get(id) == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        registry.delete(id);
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
