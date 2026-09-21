package com.spark.projectanalysis.service;

import com.spark.projectanalysis.engine.CallGraphBuilder;
import com.spark.projectanalysis.engine.ClassMetadataRegistry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分析编排：注册表构建/类搜索委托 {@link ClassMetadataService}，
 * 本类聚焦 入口解析 → 树构建 → 统计 → 内存/持久化缓存。
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final int DEFAULT_MAX_DEPTH = 20;
    /**
     * 每个入口方法的独立节点预算（graph builder 为每个 root 各分配一份，见 CallGraphBuilder#buildRoots）。
     * 单一来源（OPT-31）：BatchAnalyzeService 复用本常量，避免两处各写一份 50000。
     */
    static final int MAX_NODES = 50000;
    /** 方法调用次数分析：仅保留被调最高的 Top N 个去重方法（按被调次数降序），避免响应体积膨胀（OPT-19） */
    private static final int DEFAULT_METHOD_TOP_N = 200;
    /** 每个方法调用方采集上限（不再截断，完整采集供单方法导出使用） */
    private static final int CALLER_CAPTURE_LIMIT = Integer.MAX_VALUE;

    /** 最近一次分析结果缓存，供 Excel 导出复用，避免重复分析 */
    private volatile AnalysisResult lastResult;
    private volatile String lastResultProjectPath;

    private final AnalysisCacheService cacheService;
    private final ClassMetadataService classMetadataService;

    public AnalysisService(AnalysisCacheService cacheService, ClassMetadataService classMetadataService) {
        this.cacheService = cacheService;
        this.classMetadataService = classMetadataService;
    }

    // ------------------------------------------------------------------
    // 对外能力
    // ------------------------------------------------------------------

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
        ClassMetadataService.CacheSlot slot = classMetadataService.obtainRegistry(req.getProjectPath());
        ClassMetadataRegistry registry = slot.registry;
        ProjectLayout layout = slot.layout;

        // --- 3. 确定入口 ---
        List<MethodKey> roots;
        String className = null;
        String methodName = null;
        if (multiEntry) {
            roots = new ArrayList<>();
            for (EntryRef ref : req.getEntries()) {
                String owner = classMetadataService.resolveEntryClass(registry, ref.getClassName());
                ClassInfo info = registry.get(owner);
                roots.addAll(classMetadataService.entryMethods(info, ref.getMethodName(), ref.getMethodDescriptor()));
            }
        } else {
            String internalOwner = classMetadataService.resolveEntryClass(registry, req.getClassName());
            className = internalOwner.replace('/', '.');
            ClassInfo classInfo = registry.get(internalOwner);
            roots = classMetadataService.entryMethods(classInfo, req.getMethodName());
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

    /** 由调用图组装完整分析结果（复用统计/频次汇总逻辑），供批量分析服务复用 */
    public AnalysisResult assembleResult(String projectPath, ClassMetadataService.RegistryHandle handle,
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
}
