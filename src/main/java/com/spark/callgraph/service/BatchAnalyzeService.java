package com.spark.callgraph.service;

import com.spark.callgraph.engine.CallGraphBuilder;
import com.spark.callgraph.engine.model.CallGraph;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.AnalyzeRequest;
import com.spark.callgraph.service.dto.BatchAnalyzeStatus;
import com.spark.callgraph.service.dto.BatchSummary;
import com.spark.callgraph.service.dto.EntryList;
import com.spark.callgraph.service.dto.EntryRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 按交易入口清单的批量分析（异步 Job 模式，前端轮询进度）。
 *
 * 关键设计：不把全部入口的调用树聚合进一个结果（会膨胀到 GB 级，导致页面/刷新/进入项目全部卡死）。
 * 而是：逐入口分析 → 每个入口单独存一份缓存文件（ClassName#method_hash.json）→
 * 只保存一份轻量的 BatchSummary 索引（清单 + 每入口摘要 + 汇总统计）为单份缓存。
 * 前端展开某个入口时，再按文件名单独加载该入口的完整结果。
 */
@Service
public class BatchAnalyzeService {

    private static final Logger log = LoggerFactory.getLogger(BatchAnalyzeService.class);

    private static final int DEFAULT_MAX_DEPTH = 20;
    /** 每个入口方法独立的节点预算（与同步分析语义一致） */
    private static final int MAX_NODES_PER_ROOT = 50000;
    /** 频率分析来源筛选（持久化到每入口缓存文件名指纹，与同步分析一致） */
    private static final String FREQ_FILTER = "ALL";

    /** Job TTL（毫秒）：完成/失败后保留 5 分钟 */
    private static final long JOB_TTL_MS = 5 * 60 * 1000L;

    private final AnalysisService analysisService;
    private final AnalysisCacheService cacheService;
    private final EntryListService entryListService;

    /** 异步任务表 */
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    /** 异步执行器 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "batch-analyze-worker");
        t.setDaemon(true);
        return t;
    });

    public BatchAnalyzeService(AnalysisService analysisService,
                               AnalysisCacheService cacheService,
                               EntryListService entryListService) {
        this.analysisService = analysisService;
        this.cacheService = cacheService;
        this.entryListService = entryListService;
    }

    /** 启动异步批量分析，返回 jobId */
    public String startAsync(AnalyzeRequest req) {
        if (req == null || req.getProjectPath() == null || req.getProjectPath().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不能为空");
        }
        if (req.getEntries() == null || req.getEntries().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "交易入口清单为空，请先在 Step 2 确认入口");
        }
        if (!Files.exists(Paths.get(req.getProjectPath().trim()))) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不存在: " + req.getProjectPath());
        }

        String jobId = UUID.randomUUID().toString().substring(0, 8);
        Job job = new Job(jobId, req);
        jobs.put(jobId, job);
        executor.submit(() -> runJob(job));
        cleanupExpired();
        return jobId;
    }

    /** 查询任务状态（轻量：不含结果，DONE 后前端自行加载单份缓存） */
    public BatchAnalyzeStatus status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return null;
        return job.toStatus();
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void runJob(Job job) {
        AnalyzeRequest req = job.req;
        String path = req.getProjectPath().trim();
        long start = System.currentTimeMillis();
        try {
            int maxDepth = req.getMaxDepth() == null
                    ? DEFAULT_MAX_DEPTH
                    : Math.max(1, Math.min(req.getMaxDepth(), 50));

            // 5-40%：注册表构建（复用缓存，回调真实渐增）
            job.update(BatchAnalyzeStatus.State.INDEXING, 5, "构建类注册表...");
            AnalysisService.RegistryHandle handle = analysisService.registryFor(path,
                    (phase, done, total, desc) -> {
                        switch (phase) {
                            case 0: job.update(BatchAnalyzeStatus.State.INDEXING, 10, desc); break;
                            case 1: job.update(BatchAnalyzeStatus.State.INDEXING, 15, desc); break;
                            case 2: {
                                int base = 15;
                                int span = 20; // 15~35%
                                int pct = total > 0 ? base + (int) ((done * 100.0 / total) * span / 100) : base;
                                job.update(BatchAnalyzeStatus.State.INDEXING, Math.min(35, pct), desc);
                                break;
                            }
                            case 3: job.update(BatchAnalyzeStatus.State.INDEXING, 40, "注册表就绪"); break;
                        }
                    });

            // 40-100%：逐入口分析 → 每入口单独落盘 → 汇总轻量索引
            List<EntryRef> entries = req.getEntries();
            job.total = entries.size();
            CallGraphBuilder builder = new CallGraphBuilder(handle.getRegistry());

            BatchSummary summary = new BatchSummary();
            summary.setAnalyzedAt(System.currentTimeMillis());
            AnalysisResult.Stats combined = summary.getStats();
            combined.setEntryCount(entries.size());
            int failed = 0;
            Set<String> written = new HashSet<>();   // 本批成功落盘的入口文件名，用于清扫旧文件

            for (int i = 0; i < entries.size(); i++) {
                EntryRef ref = entries.get(i);
                String desc = "[" + (i + 1) + "/" + entries.size() + "] 分析 "
                        + simpleName(ref.getClassName()) + "#" + (ref.getMethodName() == null ? "" : ref.getMethodName());
                int pct = 42 + (int) ((i + 1) * 53.0 / entries.size());
                job.update(BatchAnalyzeStatus.State.ANALYZING, Math.min(95, pct), desc, i + 1, entries.size());

                BatchSummary.Entry entry = new BatchSummary.Entry();
                entry.setClassName(ref.getClassName());
                entry.setMethodName(ref.getMethodName());
                entry.setDescriptor(ref.getMethodDescriptor());
                summary.getEntries().add(entry);

                try {
                    long entryStart = System.currentTimeMillis();
                    List<MethodKey> roots =
                            analysisService.resolveEntryRoots(handle.getRegistry(), ref);
                    if (roots.isEmpty()) {
                        entry.setFailed(true);
                        entry.setError("未解析到根方法");
                        failed++;
                        continue;
                    }
                    CallGraph graph = builder.buildGraphRoots(roots, maxDepth, MAX_NODES_PER_ROOT);

                    AnalysisResult result = analysisService.assembleResult(
                            path, handle, graph, System.currentTimeMillis() - entryStart);
                    result.setClassName(ref.getClassName());
                    result.setMethodName(ref.getMethodName());

                    // 每个入口单独落盘（文件名与同步分析一致，可被 loadByFileName 按名加载）
                    cacheService.save(path, ref.getClassName(), ref.getMethodName(),
                            maxDepth, MAX_NODES_PER_ROOT, FREQ_FILTER, result);

                    entry.setFileName(cacheService.fileNameOf(ref.getClassName(), ref.getMethodName(),
                            maxDepth, MAX_NODES_PER_ROOT, FREQ_FILTER));
                    written.add(entry.getFileName());
                    entry.setStats(result.getStats());

                    // 汇总统计（时长取总和，其余累加）
                    combined.setTotalNodes(combined.getTotalNodes() + result.getStats().getTotalNodes());
                    combined.setProjectMethods(combined.getProjectMethods() + result.getStats().getProjectMethods());
                    combined.setDependencyMethods(combined.getDependencyMethods() + result.getStats().getDependencyMethods());
                    combined.setExternalMethods(combined.getExternalMethods() + result.getStats().getExternalMethods());
                    combined.setEdgeCount(combined.getEdgeCount() + result.getStats().getEdgeCount());
                    combined.setTruncated(combined.isTruncated() || result.getStats().isTruncated());
                    combined.setDurationMs(combined.getDurationMs() + result.getStats().getDurationMs());
                } catch (Exception e) {
                    failed++;
                    entry.setFailed(true);
                    entry.setError(e.getMessage() == null ? "分析失败" : e.getMessage());
                    log.warn("[批量分析] 入口失败，已跳过: {} -> {}", entryDesc(ref), e.getMessage());
                }
            }
            if (failed > 0) {
                summary.getWarnings().add("批量分析中有 " + failed + " 个入口失败（类/方法不存在等），已跳过，可在清单中逐个展开查看原因");
            }
            combined.setDurationMs(System.currentTimeMillis() - start);

            // 保存轻量索引为单份缓存（前端进入项目时加载的就是这份，KB 级）
            EntryList entryList = entryListService.load(path);
            cacheService.saveSingle(path, summary,
                    cacheService.entryListFingerprint(entryList.getConfirmed()),
                    entryList.getConfirmed().size());

            // 清扫本批未覆盖的旧入口文件（代码变更产生的旧指纹版本 / 已移除入口）
            if (!written.isEmpty()) {
                cacheService.pruneBatchEntries(path, written);
            }

            job.update(BatchAnalyzeStatus.State.DONE, 100,
                    "✓ 完成！成功 " + (entries.size() - failed) + " 个，失败 " + failed + " 个",
                    entries.size(), entries.size());
            log.info("[批量分析] 完成 entries={}, failed={}, totalNodes={}, duration={}ms",
                    entries.size(), failed, combined.getTotalNodes(), combined.getDurationMs());
        } catch (Exception e) {
            log.error("[批量分析] 失败", e);
            job.fail(e.getMessage() == null ? "未知错误" : e.getMessage());
        }
    }

    private static String entryDesc(EntryRef ref) {
        return ref.getClassName() + "#" + (ref.getMethodName() == null ? "" : ref.getMethodName());
    }

    private static String simpleName(String className) {
        if (className == null) return "";
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    // ------------------------------------------------------------------
    // Job 内部类
    // ------------------------------------------------------------------

    private static final class Job {
        final String jobId;
        final AnalyzeRequest req;
        volatile BatchAnalyzeStatus.State state = BatchAnalyzeStatus.State.QUEUED;
        volatile int progress;
        volatile String step = "排队中...";
        volatile int done;
        volatile int total;
        volatile String error;
        final long createdAt = System.currentTimeMillis();
        volatile long doneAt = 0;

        Job(String jobId, AnalyzeRequest req) {
            this.jobId = jobId;
            this.req = req;
        }

        void update(BatchAnalyzeStatus.State s, int p, String step) {
            this.state = s;
            this.progress = Math.min(100, Math.max(0, p));
            this.step = step;
        }

        void update(BatchAnalyzeStatus.State s, int p, String step, int done, int total) {
            update(s, p, step);
            this.done = done;
            this.total = total;
        }

        void fail(String err) {
            this.state = BatchAnalyzeStatus.State.FAILED;
            this.progress = 100;
            this.step = "分析失败";
            this.error = err;
            this.doneAt = System.currentTimeMillis();
        }

        BatchAnalyzeStatus toStatus() {
            BatchAnalyzeStatus s = new BatchAnalyzeStatus();
            s.setJobId(jobId);
            s.setState(state);
            s.setProgress(progress);
            s.setStep(step);
            s.setDone(done);
            s.setTotal(total);
            if (state == BatchAnalyzeStatus.State.FAILED) s.setError(error);
            return s;
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> {
            Job j = e.getValue();
            if (j.doneAt == 0) return false; // 还在跑
            return now - j.doneAt > JOB_TTL_MS;
        });
    }
}
