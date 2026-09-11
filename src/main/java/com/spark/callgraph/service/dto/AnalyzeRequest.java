package com.spark.callgraph.service.dto;

import java.util.List;

/** 分析请求 */
public class AnalyzeRequest {
    private String projectPath;
    private String className;
    private String methodName; // 可空：为空时分析整个类
    private Integer maxDepth;  // 可空：默认 20
    private List<EntryRef> entries; // 可空：多入口分析（来自入口扫描勾选）

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }
    public List<EntryRef> getEntries() { return entries; }
    public void setEntries(List<EntryRef> entries) { this.entries = entries; }
}
