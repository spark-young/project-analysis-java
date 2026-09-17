package com.spark.projectanalysis.service;

import com.spark.projectanalysis.engine.CallGraphBuilder;
import com.spark.projectanalysis.engine.ClassMetadataRegistry;
import com.spark.projectanalysis.engine.ClasspathResolver;
import com.spark.projectanalysis.engine.MavenRepoLocator;
import com.spark.projectanalysis.engine.ProjectLayout;
import com.spark.projectanalysis.engine.model.CallGraph;
import com.spark.projectanalysis.engine.model.ClassInfo;
import com.spark.projectanalysis.engine.model.GraphEdge;
import com.spark.projectanalysis.engine.model.GraphMethod;
import com.spark.projectanalysis.engine.model.MethodKey;
import com.spark.projectanalysis.engine.model.SourceType;
import com.spark.projectanalysis.service.dto.AnalysisResult;
import com.spark.projectanalysis.service.dto.AnalyzeRequest;
import com.spark.projectanalysis.service.dto.EntryRef;
import com.spark.projectanalysis.service.dto.MethodCaller;
import com.spark.projectanalysis.service.dto.MethodFrequency;
import com.spark.projectanalysis.service.dto.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 分析编排：布局识别 → 注册表构建（缓存复用）→ 入口解析 → 树构建 → 统计。
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final int DEFAULT_MAX_DEPTH = 20;
    private static final int MAX_NODES = 50000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;
    /** 方法调用次数分析：仅保留被调最高的 Top N 个去重方法（按被调次数降序），避免响应体积膨胀（OPT-19） */
    private static final int DEFAULT_METHOD_TOP_N = 200;
    /** 每个方法调用方采集上限（不再截断，完整采集供单方法导出使用） */
    private static final int CALLER_CAPTURE_LIMIT = Integer.MAX_VALUE;

    /** 单槽缓存：同项目连续分析（树/Excel/类搜索）无需重建注册表 */
    private volatile CacheSlot cache;

    /** 最近一次分析结果缓存，供 Excel 导出复用，避免重复分析 */
    private volatile AnalysisResult lastResult;
    private volatile String lastResultProjectPath;

    private final AnalysisCacheService cacheService;

    public AnalysisService(AnalysisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    private static final class CacheSlot {
        final String path;
        final ProjectLayout layout;
        final ClassMetadataRegistry registry;
        final long ts;

        CacheSlot(String path, ProjectLayout layout, ClassMetadataRegistry registry) {
            this.path = path;
            this.layout = layout;
            this.registry = registry;
            this.ts = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------
    // 对外能力
    // ------------------------------------------------------------------

    public ProjectInfo projectInfo(String path) {
        try (ProjectLayout layout = ClasspathResolver.resolve(Paths.get(path), defaultMavenRepo())) {
            ProjectInfo info = new ProjectInfo();
            info.setPath(path);
            info.setName(layout.getProjectName());
            info.setLayoutType(layout.getType().name());
            info.setLayoutLabel(layout.getType().getLabel());
            info.setProjectClassCount(countClassFiles(layout));
            info.setDependencyJarCount(layout.getDependencyJars().size());
            info.setWarnings(new ArrayList<>(layout.getWarnings()));
            return info;
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "读取项目失败: " + e.getMessage());
        }
    }

    /** 注册表句柄：供入口扫描等服务复用缓存注册表 */
    public RegistryHandle registryFor(String projectPath) {
        return registryFor(projectPath, null);
    }

    /** 进度回调：progressCb.accept(phase, done, total, desc)
     *  phase 0=解析classpath, 1=开始索引, 2=索引中（会被 Builder 多次触发，done/total 为 entry 级）, 3=完成 */
    public RegistryHandle registryFor(String projectPath, ProgressCallback progressCb) {
        CacheSlot slot = obtainRegistry(projectPath, progressCb);
        return new RegistryHandle(slot.registry, slot.layout.getProjectName(),
                slot.layout.getType().name(), new ArrayList<>(slot.layout.getWarnings()));
    }

    /** 自定义进度回调接口 */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(int phase, int done, int total, String desc);
    }

    /** 注册表 + 项目信息（只读快照） */
    public static final class RegistryHandle {
        private final ClassMetadataRegistry registry;
        private final String projectName;
        private final String layoutType;
        private final List<String> warnings;

        RegistryHandle(ClassMetadataRegistry registry, String projectName,
                       String layoutType, List<String> warnings) {
            this.registry = registry;
            this.projectName = projectName;
            this.layoutType = layoutType;
            this.warnings = warnings;
        }

        public ClassMetadataRegistry getRegistry() { return registry; }
        public String getProjectName() { return projectName; }
        public String getLayoutType() { return layoutType; }
        public List<String> getWarnings() { return warnings; }
    }

    public List<String> searchClasses(String path, String q) {
        if (q == null || q.trim().isEmpty()) return new ArrayList<>();
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String needle = q.trim();
        List<String> startsWith = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != SourceType.PROJECT) continue;
            String fqcn = ci.getInternalName().replace('/', '.');
            if (fqcn.startsWith(needle)) {
                startsWith.add(fqcn);
            } else if (fqcn.toLowerCase().contains(needle.toLowerCase())) {
                contains.add(fqcn);
            }
            if (startsWith.size() >= 20) break;
        }
        List<String> out = new ArrayList<>(startsWith);
        for (String c : contains) {
            if (out.size() >= 20) break;
            out.add(c);
        }
        return out;
    }

    /** 返回某个类的所有方法（name + descriptor），供前端下拉选 */
    public List<Map<String, String>> getMethods(String path, String fullClassName) {
        List<Map<String, String>> out = new ArrayList<>();
        if (fullClassName == null || fullClassName.trim().isEmpty()) return out;
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String internalName = fullClassName.trim().replace('.', '/');
        ClassInfo ci = registry.get(internalName);
        if (ci == null) return out;
        for (var mk : ci.methodKeys()) {
            Map<String, String> m = new HashMap<>();
            m.put("name", mk.getName());
            m.put("descriptor", mk.getDescriptor());
            out.add(m);
        }
        // 按方法名排序（同名不同重载放一起）
        out.sort((a, b) -> a.get("name").compareTo(b.get("name")));
        return out;
    }

    /** 验证 className + methodName + descriptor 是否在项目中真实存在 */
    public Map<String, Object> verifyEntry(String path, String className, String methodName, String descriptor) {
        Map<String, Object> resp = new HashMap<>();
        if (className == null || className.trim().isEmpty()) {
            resp.put("ok", false); resp.put("reason", "类名不能为空"); return resp;
        }
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String internalName = className.trim().replace('.', '/');
        ClassInfo ci = registry.get(internalName);
        if (ci == null) {
            // 再试 simpleName 匹配
            String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
            var candidates = registry.classesBySimpleName(simple);
            if (candidates == null || candidates.isEmpty()) {
                resp.put("ok", false); resp.put("reason", "项目中未找到类: " + className); return resp;
            }
            if (candidates.size() == 1) {
                ci = registry.get(candidates.iterator().next());
            } else {
                resp.put("ok", false);
                resp.put("reason", "找到 " + candidates.size() + " 个同名类，请填全限定名");
                List<String> fullNames = new ArrayList<>();
                for (String cn : candidates) fullNames.add(cn.replace('/', '.'));
                resp.put("candidates", fullNames);
                return resp;
            }
        }
        // class 存在
        if (methodName == null || methodName.trim().isEmpty()) {
            resp.put("ok", true); resp.put("reason", "类存在: " + ci.getInternalName().replace('/', '.'));
            return resp;
        }
        // method 验证
        String mname = methodName.trim();
        String desc = descriptor == null ? "" : descriptor.trim();
        if (desc.isEmpty()) {
            // 不指定 descriptor → 只要有同名方法就算通过
            boolean found = false;
            List<String> descs = new ArrayList<>();
            for (var mk : ci.methodKeys()) {
                if (mk.getName().equals(mname)) { found = true; descs.add(mk.getDescriptor()); }
            }
            if (!found) {
                resp.put("ok", false);
                resp.put("reason", "类存在但方法 " + mname + " 不存在");
                resp.put("available", ci.methodKeys().stream().map(mk -> mk.getName()).distinct().collect(Collectors.toList()));
                return resp;
            }
            if (descs.size() == 1) {
                resp.put("ok", true);
                resp.put("descriptor", descs.get(0));
                resp.put("reason", "✓ 已匹配唯一重载，建议 descriptor: " + descs.get(0));
            } else {
                resp.put("ok", true);
                resp.put("multipleOverloads", descs);
                resp.put("reason", "✓ 方法存在但有 " + descs.size() + " 个重载，建议指定 descriptor 精确匹配");
            }
            return resp;
        }
        // descriptor 也指定了
        if (ci.hasOwnMethod(mname, desc)) {
            resp.put("ok", true);
            resp.put("reason", "✓ 完整匹配: " + className + "#" + mname + desc);
        } else {
            resp.put("ok", false);
            resp.put("reason", "类存在，但未找到方法 " + mname + desc);
        }
        return resp;
    }

    public AnalysisResult analyze(AnalyzeRequest req) {
        long start = System.currentTimeMillis();
        validate(req);
        int maxDepth = req.getMaxDepth() == null
                ? DEFAULT_MAX_DEPTH
                : Math.max(1, Math.min(req.getMaxDepth(), 50));

        // --- 1. 持久化缓存命中检查（多入口模式不走缓存） ---
        boolean useCache = req.getSkipCache() == null || !req.getSkipCache();
        boolean multiEntry = req.getEntries() != null && !req.getEntries().isEmpty();
        if (useCache && !multiEntry) {
            String freqFilter = req.getFreqSourceFilter() == null ? "ALL" : req.getFreqSourceFilter();
            Optional<AnalysisResult> cached = cacheService.load(
                    req.getProjectPath(), req.getClassName(), req.getMethodName(),
                    maxDepth, MAX_NODES, freqFilter);
            if (cached.isPresent()) {
                AnalysisResult hit = cached.get();
                // schema=1 旧树缓存/损坏：图为空则作废，重新分析（不沿用空结果）
                if (hit.getSchema() == 2 && !hit.getGraph().getMethods().isEmpty()) {
                    hit.setStats(hit.getStats() == null ? new AnalysisResult.Stats() : hit.getStats());
                    hit.getStats().setDurationMs(System.currentTimeMillis() - start);
                    lastResult = hit;
                    lastResultProjectPath = req.getProjectPath();
                    log.info("[缓存] 命中持久化缓存，跳过完整分析 ({}ms)", hit.getStats().getDurationMs());
                    return hit;
                }
                log.info("[缓存] 命中但 schema/图无效，作废重析");
            }
        }

        // --- 2. 注册表加载 ---
        CacheSlot slot = obtainRegistry(req.getProjectPath());
        ClassMetadataRegistry registry = slot.registry;
        ProjectLayout layout = slot.layout;

        // --- 3. 确定入口 ---
        List<MethodKey> roots;
        String className = null;
        String methodName = null;
        if (multiEntry) {
            roots = new ArrayList<>();
            for (EntryRef ref : req.getEntries()) {
                String owner = resolveEntryClass(registry, ref.getClassName());
                ClassInfo info = registry.get(owner);
                roots.addAll(entryMethods(info, ref.getMethodName(), ref.getMethodDescriptor()));
            }
        } else {
            String internalOwner = resolveEntryClass(registry, req.getClassName());
            className = internalOwner.replace('/', '.');
            ClassInfo classInfo = registry.get(internalOwner);
            roots = entryMethods(classInfo, req.getMethodName());
            methodName = req.getMethodName() == null || req.getMethodName().trim().isEmpty()
                    ? null : req.getMethodName().trim();
        }

        // --- 4. 执行调用图分析（去重节点表 + 边表） ---
        CallGraphBuilder builder = new CallGraphBuilder(registry);
        CallGraph graph = builder.buildGraphRoots(roots, maxDepth, MAX_NODES);

        AnalysisResult result = new AnalysisResult();
        result.setProjectPath(req.getProjectPath());
        result.setProjectName(layout.getProjectName());
        result.setLayoutType(layout.getType().name());
        result.setClassName(className);
        result.setMethodName(methodName);
        result.setGraph(graph);
        result.setWarnings(new ArrayList<>(layout.getWarnings()));
        fillStats(result, graph, System.currentTimeMillis() - start);

        // --- 5. 内存缓存（供 Excel 导出复用） ---
        lastResult = result;
        lastResultProjectPath = req.getProjectPath();

        // --- 6. 持久化到 JSON（所有分析都落盘，跟着项目走） ---
        // 单入口：ClassName#methodName_hash.json
        // 多入口：BatchAnalysis_N_hash.json （N = 入口数）
        {
            String freqFilter = req.getFreqSourceFilter() == null ? "ALL" : req.getFreqSourceFilter();
            String saveClass, saveMethod;
            if (multiEntry) {
                saveClass = "__BatchAnalysis(" + req.getEntries().size() + "个入口)";
                saveMethod = "";
            } else {
                saveClass = req.getClassName();
                saveMethod = req.getMethodName();
            }
            cacheService.save(req.getProjectPath(), saveClass, saveMethod,
                    maxDepth, MAX_NODES, freqFilter, result);
        }

        return result;
    }

    /** 获取最近一次分析结果（仅当项目路径匹配时），用于 Excel 导出避免重复分析 */
    public AnalysisResult getLastResult(String projectPath) {
        if (lastResult != null && projectPath != null
                && projectPath.equals(lastResultProjectPath)) {
            return lastResult;
        }
        return null;
    }

    /** 记录最近一次分析结果（供批量分析服务在异步完成后复用 Excel 导出） */
    public void rememberLastResult(AnalysisResult result, String projectPath) {
        this.lastResult = result;
        this.lastResultProjectPath = projectPath;
    }

    /** 解析单个交易入口引用为根方法列表（供批量分析服务复用） */
    public List<MethodKey> resolveEntryRoots(ClassMetadataRegistry registry, EntryRef ref) {
        String owner = resolveEntryClass(registry, ref.getClassName());
        ClassInfo info = registry.get(owner);
        return entryMethods(info, ref.getMethodName(), ref.getMethodDescriptor());
    }

    /** 由调用图组装完整分析结果（复用统计/频次汇总逻辑），供批量分析服务复用 */
    public AnalysisResult assembleResult(String projectPath, RegistryHandle handle,
                                         CallGraph graph, long durationMs) {
        AnalysisResult result = new AnalysisResult();
        result.setProjectPath(projectPath);
        result.setProjectName(handle.getProjectName());
        result.setLayoutType(handle.getLayoutType());
        result.setGraph(graph);
        result.setWarnings(new ArrayList<>(handle.getWarnings()));
        fillStats(result, graph, durationMs);
        return result;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void validate(AnalyzeRequest req) {
        if (req == null || req.getProjectPath() == null || req.getProjectPath().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不能为空");
        }
        boolean hasEntries = req.getEntries() != null && !req.getEntries().isEmpty();
        if (!hasEntries && (req.getClassName() == null || req.getClassName().trim().isEmpty())) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "类名不能为空");
        }
        Path p = Paths.get(req.getProjectPath().trim());
        if (!Files.exists(p)) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不存在: " + req.getProjectPath());
        }
    }

    private synchronized CacheSlot obtainRegistry(String projectPath) {
        return obtainRegistry(projectPath, null);
    }

    private synchronized CacheSlot obtainRegistry(String projectPath, ProgressCallback progressCb) {
        String path = projectPath.trim();
        CacheSlot current = cache;
        if (current != null && current.path.equals(path)
                && System.currentTimeMillis() - current.ts < CACHE_TTL_MS) {
            if (progressCb != null) progressCb.accept(3, 1, 1, "注册表命中缓存");
            return current;
        }
        if (current != null) {
            closeQuietly(current.layout);
        }
        try {
            if (progressCb != null) progressCb.accept(0, 0, 3, "解析项目 classpath...");
            ProjectLayout layout = ClasspathResolver.resolve(Paths.get(path), defaultMavenRepo());

            int total = layout.getProjectClassDirs().size() + layout.getDependencyJars().size();
            if (progressCb != null) progressCb.accept(1, 0, total, "开始索引 " + total + " 个条目...");

            ClassMetadataRegistry.Builder builder = ClassMetadataRegistry.builder();
            for (Path dir : layout.getProjectClassDirs()) {
                builder.addClassesDir(dir, SourceType.PROJECT);
            }
            for (Path jar : layout.getDependencyJars()) {
                builder.addJar(jar, SourceType.DEPENDENCY);
            }
            builder.withProgress((done, desc) -> {
                if (progressCb != null) progressCb.accept(2, done, total, desc);
            });
            ClassMetadataRegistry registry = builder.build();

            if (progressCb != null) progressCb.accept(3, total, total, "注册表构建完成");

            CacheSlot slot = new CacheSlot(path, layout, registry);
            cache = slot;
            return slot;
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "读取项目失败: " + e.getMessage());
        }
    }

    private String resolveEntryClass(ClassMetadataRegistry registry, String classNameInput) {
        String input = classNameInput.trim();
        String internal = input.replace('.', '/');
        if (registry.isKnown(internal)) return internal;
        Collection<String> bySimple = registry.classesBySimpleName(input);
        if (bySimple.isEmpty()) {
            throw new AnalysisException(HttpStatus.NOT_FOUND,
                    "类未找到（项目类中无匹配）: " + input);
        }
        if (bySimple.size() > 1) {
            throw new AnalysisException(HttpStatus.CONFLICT,
                    "存在 " + bySimple.size() + " 个同名类，请使用全限定名: "
                            + bySimple.stream().map(n -> n.replace('/', '.'))
                            .sorted().collect(Collectors.joining(", ")));
        }
        return bySimple.iterator().next();
    }

    private List<MethodKey> entryMethods(ClassInfo classInfo, String methodName) {
        return entryMethods(classInfo, methodName, null);
    }

    private List<MethodKey> entryMethods(ClassInfo classInfo, String methodName, String methodDescriptor) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return classInfo.methodKeys().stream()
                    .filter(m -> !isSyntheticOrBridge(classInfo, m))
                    .collect(Collectors.toList());
        }
        String name = methodName.trim();
        List<MethodKey> matching = classInfo.methodKeys().stream()
                .filter(m -> m.getName().equals(name))
                .collect(Collectors.toList());
        if (methodDescriptor != null && !methodDescriptor.trim().isEmpty()) {
            String desc = methodDescriptor.trim();
            List<MethodKey> exact = matching.stream()
                    .filter(m -> m.getDescriptor().equals(desc))
                    .collect(Collectors.toList());
            if (!exact.isEmpty()) matching = exact;
        }
        if (matching.isEmpty()) {
            String available = classInfo.methodKeys().stream()
                    .map(MethodKey::getName)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AnalysisException(HttpStatus.NOT_FOUND,
                    "方法未找到: " + classInfo.getInternalName().replace('/', '.') + "." + name
                            + "。该类可用方法: " + available);
        }
        return matching;
    }

    private boolean isSyntheticOrBridge(ClassInfo ci, MethodKey m) {
        return (ci.methodAccess(m.getName(), m.getDescriptor()) & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0;
    }

    private void fillStats(AnalysisResult result, CallGraph graph, long durationMs) {
        AnalysisResult.Stats stats = result.getStats();
        stats.setEntryCount(graph.getRoots().size());
        stats.setTotalNodes(graph.getMethods().size());
        stats.setEdgeCount(graph.getEdges().size());
        stats.setTruncated(graph.isTruncated());
        stats.setDurationMs(durationMs);
        // 去重后的独立方法数（节点表全量，按 source 分类）
        int project = 0, dep = 0, ext = 0;
        for (GraphMethod m : graph.getMethods()) {
            switch (m.getSource()) {
                case PROJECT:     project++; break;
                case DEPENDENCY:  dep++;     break;
                default:          ext++;     break;
            }
        }
        stats.setProjectMethods(project);
        stats.setDependencyMethods(dep);
        stats.setExternalMethods(ext);
        result.setMethodFrequency(topFrequency(collectGraphStats(graph)));
    }

    /** 由图边表汇总：被调次数 = 入边数，调用方 = 入边来源方法id + 行号 */
    private Map<MethodKey, MethodAgg> collectGraphStats(CallGraph graph) {
        Map<MethodKey, MethodAgg> agg = new HashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            GraphMethod target = graph.getMethods().get(edge.getTo());
            MethodKey targetKey = target.toKey();
            MethodAgg a = agg.computeIfAbsent(targetKey, k -> new MethodAgg());
            if (a.source == null) a.source = target.getSource();
            a.methodId = edge.getTo();          // 被调方法在节点表中的下标（存 id，不存签名）
            a.callCount++;
            if (a.callers.size() < CALLER_CAPTURE_LIMIT) {
                a.callers.add(new CallerInfo(edge.getFrom(), edge.getLine()));
            }
        }
        return agg;
    }

    /** 构建"高频被调方法"排行：被调次数降序，同次数按标识稳定序，截取 Top N */
    private List<MethodFrequency> topFrequency(Map<MethodKey, MethodAgg> agg) {
        return agg.entrySet().stream()
                .filter(e -> e.getValue().callCount > 0)
                .sorted((a, b) -> {
                    int r = Integer.compare(b.getValue().callCount, a.getValue().callCount);
                    return r != 0 ? r : a.getKey().getIdentifier().compareTo(b.getKey().getIdentifier());
                })
                .limit(DEFAULT_METHOD_TOP_N)
                .map(e -> toFrequency(e.getValue()))
                .collect(Collectors.toList());
    }

    private MethodFrequency toFrequency(MethodAgg a) {
        MethodFrequency f = new MethodFrequency();
        f.setMethodId(a.methodId);
        f.setSource(a.source.name());
        f.setCallCount(a.callCount);
        f.setCallers(a.callers.stream().map(ci -> {
            MethodCaller mc = new MethodCaller();
            mc.setCallerId(ci.callerId);
            mc.setLine(ci.line);
            return mc;
        }).collect(Collectors.toList()));
        return f;
    }

    /** 统计收集器：去重方法结构（节点 id + 入度 + 调用方集合） */
    private static final class MethodAgg {
        final List<CallerInfo> callers = new ArrayList<>();
        int callCount;      // 入度：被调次数（根方法为 0，不进排行）
        SourceType source;  // 首次出现时的来源（owner 固定，source 稳定）
        int methodId = -1;  // 被调方法在 graph.methods 中的下标
    }

    private static final class CallerInfo {
        final int callerId; // 调用方在 graph.methods 中的下标
        final int line;     // 调用处行号，未知 -1
        CallerInfo(int callerId, int line) {
            this.callerId = callerId;
            this.line = line;
        }
    }

    private int countClassFiles(ProjectLayout layout) {
        int count = 0;
        for (Path dir : layout.getProjectClassDirs()) {
            try (Stream<Path> walk = Files.walk(dir)) {
                count += (int) walk.filter(p -> p.toString().endsWith(".class")).count();
            } catch (IOException ignore) {
                // 计数失败忽略
            }
        }
        return count;
    }

    private Path defaultMavenRepo() {
        return MavenRepoLocator.detect();
    }

    /**
     * 页面默认演示值：默认分析本工具自身。
     * projectPath 取运行时 classpath 位置——fat jar 运行即分析该 jar（自带依赖，随处可用）；
     * classes 目录运行（IDE/mvn）则回退到项目根目录。
     */
    public AnalyzeRequest demoDefaults() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setClassName("com.spark.projectanalysis.service.AnalysisService");
        req.setMethodName("analyze");
        req.setProjectPath(selfLocation());
        return req;
    }

    private String selfLocation() {
        try {
            java.net.URL url = AnalysisService.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            String spec = url.toString();
            // Spring Boot fat jar：内嵌 classes 的 code source 形如
            // jar:file:/path/project-analysis-java.jar!/BOOT-INF/classes!/ → 取外层 jar 本身
            if (spec.startsWith("jar:file:")) {
                int idx = spec.indexOf("!/");
                if (idx > 0) {
                    Path jar = Paths.get(new java.net.URI(spec.substring(4, idx)));
                    if (Files.isRegularFile(jar)) return jar.toString();
                }
            }
            Path p = Paths.get(url.toURI());
            if (Files.isRegularFile(p)) {
                return p.toString(); // jar：直接作为 fat jar 分析
            }
            Path parent = p.getParent();
            return parent != null && parent.getParent() != null
                    ? parent.getParent().toString() // target/classes → 项目根
                    : p.toString();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    private void closeQuietly(ProjectLayout layout) {
        try {
            layout.close();
        } catch (IOException ignore) {
            // 清理失败忽略
        }
    }
}
