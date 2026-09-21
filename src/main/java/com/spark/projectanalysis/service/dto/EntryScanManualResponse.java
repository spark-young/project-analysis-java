package com.spark.projectanalysis.service.dto;

import java.util.List;

/**
 * POST /api/projects/{id}/entries/scan-manual 响应。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryScanManualResponse {

    private List<EntryList.EntryItem> candidates;
    private int existed;
    private int total;
    private String projectPath;
    /** CLASS = 填的是类；PACKAGE = 按包名处理；NONE = 既不是类也不是包 */
    private String mode;
    /** 解析出的全限定类名 / 包名 */
    private String resolvedName;
    /** 命中数超过上限，只返回了前 N 个 */
    private boolean truncated;

    public List<EntryList.EntryItem> getCandidates() { return candidates; }
    public void setCandidates(List<EntryList.EntryItem> candidates) { this.candidates = candidates; }
    public int getExisted() { return existed; }
    public void setExisted(int existed) { this.existed = existed; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getResolvedName() { return resolvedName; }
    public void setResolvedName(String resolvedName) { this.resolvedName = resolvedName; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
}
