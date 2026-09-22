package com.spark.projectanalysis.service.dto;

/**
 * POST /api/scan-strategy/profile/new 响应：{profileId}。
 * 字段与原 Map<String,String> 版本逐字一致（OPT-28 切片3）。
 */
public class ProfileNewResponse {

    private String profileId;

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
}
