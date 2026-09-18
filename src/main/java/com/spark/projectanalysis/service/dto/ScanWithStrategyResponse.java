package com.spark.projectanalysis.service.dto;

/**
 * POST /api/scan-strategy/scan/{projectId} 响应。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片3）：
 * 在 EntryScanDiffResponse（candidates/existed/scanTotalGroups/scanTotalEntries/projectPath）
 * 基础上追加 profileName。
 */
public class ScanWithStrategyResponse extends EntryScanDiffResponse {

    private String profileName;

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
}
