package com.spark.callgraph.service;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.entry.EntryPoint;
import com.spark.callgraph.engine.entry.EntryPointDetector;
import com.spark.callgraph.service.dto.EntryScanResult;
import com.spark.callgraph.service.dto.EntryScanStatus;
import com.spark.callgraph.service.dto.EntryScanStatus.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 入口扫描编排：构建注册表（复用缓存）→ 运行全部探测器 → 按类型分组。
 * 同时提供异步 Job 模式供前端轮询进度。
 */
@Service
public class EntryScanService {

    private static final Logger log = LoggerFactory.getLogger(EntryScanService.class);

    /** 分组展示顺序 */
    private static final List<String> TYPE_ORDER =
            Arrays.asList("REST", "DUBBO", "ELASTIC_JOB", "MAIN");

    /** Job TTL（毫秒）：完成/失败后保留 5 分钟 */
    private static final long JOB_TTL_MS = 5 * 60 * 1000L;

    private final AnalysisService analysisService;
    private final List<EntryPointDetector> detectors;

    /** 异步任务表 */
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    /** 异步执行器 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "entry-scan-worker");
        t.setDaemon(true);
        return t;
    });

    public EntryScanService(AnalysisService analysisService, List<EntryPointDetector> detectors) {
        this.analysisService = analysisService;
        this.detectors = detectors;
    }

    // ------------------------------------------------------------------
    // 异步模式
    // ------------------------------------------------------------------

    /** 启动异步扫描，返回 jobId */
    public String startAsync(String projectPath) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        Job job = new Job(jobId, projectPath);
        jobs.put(jobId, job);

        executor.submit(() -> runScan(job));

        // 清理老任务（非阻塞）
        cleanupExpired();
        return jobId;
    }

    /** 查询任务状态 */
    public EntryScanStatus status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return null;
        return job.toStatus();
    }

    // ------------------------------------------------------------------
    // 原同步入口（保留，不影响其他调用方）
    // ------------------------------------------------------------------

    public EntryScanResult scan(String projectPath) {
        Job job = new Job("sync", projectPath);
        runScan(job);
        if (job.error != null) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, job.error);
        }
        return job.result;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void runScan(Job job) {
        try {
            // 0-5%：校验
            if (job.projectPath == null || job.projectPath.trim().isEmpty()) {
                job.fail("项目路径不能为空");
                return;
            }
            if (!Files.exists(Paths.get(job.projectPath.trim()))) {
                job.fail("项目路径不存在: " + job.projectPath);
                return;
            }
            String path = job.projectPath.trim();

            job.update(State.INDEXING, 5, "准备扫描注册表...");

            // 5-85%：注册表构建（Builder 回调每个 entry，真实渐增）
            // phase 0→10%, phase 1→20%, phase 2→20~80% 按 entry 比例, phase 3→85%
            AnalysisService.RegistryHandle handle = analysisService.registryFor(path, new AnalysisService.ProgressCallback() {
                @Override
                public void accept(int phase, int done, int total, String desc) {
                    switch (phase) {
                        case 0:
                            job.update(State.INDEXING, 10, desc);
                            break;
                        case 1:
                            job.update(State.INDEXING, 20, desc);
                            break;
                        case 2: {
                            int base = 20;
                            int span = 60; // 20~80%
                            int pct = total > 0 ? base + (int) ((done * 100.0 / total) * span / 100) : base;
                            job.update(State.INDEXING, Math.min(80, pct), desc);
                            break;
                        }
                        case 3:
                            job.update(State.INDEXING, 85, "注册表就绪，开始检测入口...");
                            break;
                    }
                }
            });

            // 85-95%：逐个 detector
            Map<String, List<EntryPoint>> grouped = new LinkedHashMap<>();
            ClassMetadataRegistry registry = handle.getRegistry();
            int detCount = detectors.size();
            for (int i = 0; i < detCount; i++) {
                EntryPointDetector d = detectors.get(i);
                job.update(State.DETECTING,
                        85 + (int) ((i + 1) * 10.0 / detCount),
                        "检测 " + d.type() + " 入口...");
                List<EntryPoint> found = d.detect(registry);
                if (!found.isEmpty()) grouped.put(d.type(), found);
            }

            // 95-100%：分组 + 序列化
            job.update(State.DETECTING, 95, "整理结果...");

            EntryScanResult result = new EntryScanResult();
            result.setProjectPath(path);
            result.setProjectName(handle.getProjectName());
            List<EntryScanResult.Group> groups = new ArrayList<>();
            grouped.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(order(a.getKey()), order(b.getKey())))
                    .forEach(e -> {
                        EntryScanResult.Group g = new EntryScanResult.Group();
                        g.setType(e.getKey());
                        g.setLabel(labelOf(e.getKey()));
                        for (EntryPoint p : e.getValue()) {
                            EntryScanResult.EntryDto dto = new EntryScanResult.EntryDto();
                            dto.setClassName(p.getClassName());
                            dto.setMethodName(p.getMethodName());
                            dto.setMethodDescriptor(p.getMethodDescriptor());
                            dto.setDisplay(p.getDisplay());
                            g.getEntries().add(dto);
                        }
                        groups.add(g);
                    });
            result.setGroups(groups);

            job.result = result;
            job.update(State.DONE, 100, "扫描完成");
        } catch (Exception e) {
            log.error("[EntryScan] 扫描失败", e);
            job.fail(e.getMessage() == null ? "未知错误" : e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Job 内部类
    // ------------------------------------------------------------------

    private static final class Job {
        final String jobId;
        final String projectPath;
        volatile State state = State.QUEUED;
        volatile int progress;
        volatile String step = "排队中...";
        volatile EntryScanResult result;
        volatile String error;
        final long createdAt = System.currentTimeMillis();
        volatile long doneAt = 0;

        Job(String jobId, String projectPath) {
            this.jobId = jobId;
            this.projectPath = projectPath;
        }

        void update(State s, int p, String step) {
            this.state = s;
            this.progress = Math.min(100, Math.max(0, p));
            this.step = step;
        }

        void fail(String err) {
            this.state = State.FAILED;
            this.progress = 100;
            this.step = "扫描失败";
            this.error = err;
            this.doneAt = System.currentTimeMillis();
        }

        EntryScanStatus toStatus() {
            EntryScanStatus s = new EntryScanStatus();
            s.setJobId(jobId);
            s.setState(state);
            s.setProgress(progress);
            s.setStep(step);
            if (state == State.DONE) s.setResult(result);
            if (state == State.FAILED) s.setError(error);
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

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static int order(String type) {
        int i = TYPE_ORDER.indexOf(type);
        return i < 0 ? TYPE_ORDER.size() : i;
    }

    private String labelOf(String type) {
        for (EntryPointDetector d : detectors) {
            if (d.type().equals(type)) return d.label();
        }
        return type;
    }
}
