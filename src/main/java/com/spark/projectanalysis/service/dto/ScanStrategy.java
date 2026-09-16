package com.spark.projectanalysis.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描策略（顶层配置）。
 * <p>
 * 持久化于两层 JSON：
 *   全局层  D:/.callgraph/scan-strategy.json
 *   项目层  <项目>/.callgraph/scan-strategy.json（存在即覆盖全局的 activeProfileId 与 profiles）
 */
public class ScanStrategy {

    public static final String BUILTIN_STANDARD = "builtin-standard";
    public static final String BUILTIN_API_ONLY = "builtin-api-only";
    public static final String BUILTIN_JOB_ONLY = "builtin-job-only";

    private int version = 1;
    /** 当前激活方案 ID */
    private String activeProfileId = BUILTIN_STANDARD;
    private List<ScanProfile> profiles = new ArrayList<>();

    public ScanStrategy() {}

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getActiveProfileId() { return activeProfileId; }
    public void setActiveProfileId(String activeProfileId) { this.activeProfileId = activeProfileId; }
    public List<ScanProfile> getProfiles() { return profiles; }
    public void setProfiles(List<ScanProfile> profiles) { this.profiles = profiles; }

    /** 按 ID 取方案；不存在返回 null */
    public ScanProfile profile(String id) {
        if (profiles == null || id == null) return null;
        for (ScanProfile p : profiles) {
            if (id.equals(p.getId())) return p;
        }
        return null;
    }

    /** 取当前激活方案；activeProfileId 无效时回退到标准扫描 */
    public ScanProfile activeProfile() {
        ScanProfile p = profile(activeProfileId);
        if (p != null) return p;
        ScanProfile std = profile(BUILTIN_STANDARD);
        if (std != null) return std;
        return profiles == null || profiles.isEmpty() ? null : profiles.get(0);
    }
}
