package com.spark.callgraph.service.dto;

import com.spark.callgraph.engine.model.CallNode;

import java.util.ArrayList;
import java.util.List;

/** 分析结果（树 + 统计 + 告警） */
public class AnalysisResult {
    private String projectPath;
    private String projectName;
    private String layoutType;
    private String className;
    private String methodName;
    private List<String> unresolvedDependencies = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<CallNode> roots = new ArrayList<>();
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
    }

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
    public List<CallNode> getRoots() { return roots; }
    public void setRoots(List<CallNode> roots) { this.roots = roots; }
    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }
    public List<MethodFrequency> getMethodFrequency() { return methodFrequency; }
    public void setMethodFrequency(List<MethodFrequency> methodFrequency) { this.methodFrequency = methodFrequency; }
}
