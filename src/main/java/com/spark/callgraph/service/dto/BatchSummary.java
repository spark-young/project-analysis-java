package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量分析（按交易入口清单）的轻量结果索引。
 * 不再把 46 个入口的完整调用树聚合到一个 JSON（会膨胀到 GB 级），
 * 而是每个入口单独存一份缓存文件，这里只存「清单 + 每个入口的摘要 + 统计」。
 */
public class BatchSummary {

    private String kind = "batch";
    private long analyzedAt;
    private AnalysisResult.Stats stats = new AnalysisResult.Stats();
    private List<String> warnings = new ArrayList<>();
    private List<Entry> entries = new ArrayList<>();

    public static class Entry {
        private String className;
        private String methodName;
        private String descriptor;
        private String fileName;
        private boolean failed;
        private String error;
        private AnalysisResult.Stats stats = new AnalysisResult.Stats();

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getDescriptor() { return descriptor; }
        public void setDescriptor(String descriptor) { this.descriptor = descriptor; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public boolean isFailed() { return failed; }
        public void setFailed(boolean failed) { this.failed = failed; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public AnalysisResult.Stats getStats() { return stats; }
        public void setStats(AnalysisResult.Stats stats) { this.stats = stats; }
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public long getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(long analyzedAt) { this.analyzedAt = analyzedAt; }
    public AnalysisResult.Stats getStats() { return stats; }
    public void setStats(AnalysisResult.Stats stats) { this.stats = stats; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }
}
