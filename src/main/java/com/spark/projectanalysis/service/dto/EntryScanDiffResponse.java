package com.spark.projectanalysis.service.dto;

import java.util.List;

/**
 * POST /api/projects/{id}/entries/scan 响应。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryScanDiffResponse {

    private List<EntryList.EntryItem> candidates;
    private int existed;
    private int scanTotalGroups;
    private int scanTotalEntries;
    private String projectPath;

    public List<EntryList.EntryItem> getCandidates() { return candidates; }
    public void setCandidates(List<EntryList.EntryItem> candidates) { this.candidates = candidates; }
    public int getExisted() { return existed; }
    public void setExisted(int existed) { this.existed = existed; }
    public int getScanTotalGroups() { return scanTotalGroups; }
    public void setScanTotalGroups(int scanTotalGroups) { this.scanTotalGroups = scanTotalGroups; }
    public int getScanTotalEntries() { return scanTotalEntries; }
    public void setScanTotalEntries(int scanTotalEntries) { this.scanTotalEntries = scanTotalEntries; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
}
