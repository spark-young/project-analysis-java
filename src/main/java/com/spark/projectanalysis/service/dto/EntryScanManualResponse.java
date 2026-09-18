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

    public List<EntryList.EntryItem> getCandidates() { return candidates; }
    public void setCandidates(List<EntryList.EntryItem> candidates) { this.candidates = candidates; }
    public int getExisted() { return existed; }
    public void setExisted(int existed) { this.existed = existed; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
}
