package com.spark.callgraph.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.spark.callgraph.engine.model.CallGraph;

import java.util.ArrayList;
import java.util.List;

/** 分析结果（图 + 统计 + 告警） */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {
    private int schema = 2;                     // 存储格式版本：2 = 图结构（旧树格式不再兼容）
    private String projectPath;
    private String projectName;
    private String layoutType;
    private String className;
    private String methodName;
    private List<String> unresolvedDependencies = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private CallGraph graph = new CallGraph();
    private Stats stats = new Stats();
    private List<MethodFrequency> methodFrequency = new ArrayList<>();

    public static class Stats {
        private int entryCount;
        private int totalNodes;
        private int projectMethods;
        private int dependencyMethods;
        private int externalMethods;
        private boolean truncated;
        private long durationMs;
        private int edgeCount = 0;              // 图版：调用关系（边）总数

        public int getEntryCount() { return entryCount; }
        public void setEntryCount(int entryCount) { this.entryCount = entryCount; }
        public int getTotalNodes() { return totalNodes; }
        public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }
        public int getProjectMethods() { return projectMethods; }
        public void setProjectMethods(int projectMethods) { this.projectMethods = projectMethods; }
        public int getDependencyMethods() { return dependencyMethods; }
        public void setDependencyMethods(int dependencyMethods) { this.dependencyMethods = dependencyMethods; }
        public int getExternalMethods() { return externalMethods; }
        public void setExternalMethods(int externalMethods) { this.externalMethods = externalMethods; }
        public boolean isTruncated() { return truncated; }
        public void setTruncated(boolean truncated) { this.truncated = truncated; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public int getEdgeCount() { return edgeCount; }
        public void setEdgeCount(int edgeCount) { this.edgeCount = edgeCount; }
    }

    // --- 兼容视图：无（schema=2 纯图结构，前端/导出直接消费 graph） ---

    public int getSchema() { return schema; }
    public void setSchema(int schema) { this.schema = schema; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public List<String> getUnresolvedDependencies() { return unresolvedDependencies; }
    public void setUnresolvedDependencies(List<String> unresolvedDependencies) { this.unresolvedDependencies = unresolvedDependencies; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public CallGraph getGraph() { return graph; }
    public void setGraph(CallGraph graph) { this.graph = graph == null ? new CallGraph() : graph; }
    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats == null ? new Stats() : stats; }
    public List<MethodFrequency> getMethodFrequency() { return methodFrequency; }
    public void setMethodFrequency(List<MethodFrequency> methodFrequency) { this.methodFrequency = methodFrequency; }
}
