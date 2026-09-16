package com.spark.projectanalysis.service.dto;

import java.util.List;

/** 分析请求 */
public class AnalyzeRequest {
    private String projectPath;
    private String className;
    private String methodName; // 可空：为空时分析整个类
    private Integer maxDepth;  // 可空：默认 20
    private List<EntryRef> entries; // 可空：多入口分析（来自入口扫描勾选）
    private String freqSourceFilter; // 方法调用次数分析的来源筛选：ALL/PROJECT/DEPENDENCY/EXTERNAL（导出 Excel 时使用）
    private Boolean skipCache; // 为 true 时跳过持久化缓存，强制重新分析
    private String cacheFileName; // 导出 Excel 时按缓存文件名加载指定结果（批量分析展开某入口时使用）

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
    public String getFreqSourceFilter() { return freqSourceFilter; }
    public void setFreqSourceFilter(String freqSourceFilter) { this.freqSourceFilter = freqSourceFilter; }
    public Boolean getSkipCache() { return skipCache; }
    public void setSkipCache(Boolean skipCache) { this.skipCache = skipCache; }
    public String getCacheFileName() { return cacheFileName; }
    public void setCacheFileName(String cacheFileName) { this.cacheFileName = cacheFileName; }
}
