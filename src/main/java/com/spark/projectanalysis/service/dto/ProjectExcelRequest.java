package com.spark.projectanalysis.service.dto;

import java.util.List;

/**
 * 项目级 Excel 导出请求：批量分析全量加载完成后，按缓存文件名一次性导出全部入口的调用链。
 */
public class ProjectExcelRequest {
    private String projectPath;
    private List<String> cacheFiles;   // 各入口的缓存文件名（成功加载的那个）
    private String freqSourceFilter;   // ALL/PROJECT/DEPENDENCY/EXTERNAL

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public List<String> getCacheFiles() { return cacheFiles; }
    public void setCacheFiles(List<String> cacheFiles) { this.cacheFiles = cacheFiles; }

    public String getFreqSourceFilter() { return freqSourceFilter; }
    public void setFreqSourceFilter(String freqSourceFilter) { this.freqSourceFilter = freqSourceFilter; }
}