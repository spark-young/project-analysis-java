package com.spark.projectanalysis.service.dto;

import java.util.List;
import java.util.Map;

/**
 * GET /api/noise-rules/project-detail 响应（项目级噪声规则配置详情）。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片5）：
 * globalRules / globalOverrides / customRules 三个键恒出现且非 null。
 */
public class NoiseProjectDetail {

    private List<NoiseRule> globalRules;
    private Map<String, Boolean> globalOverrides;
    private List<NoiseRule> customRules;

    public List<NoiseRule> getGlobalRules() { return globalRules; }
    public void setGlobalRules(List<NoiseRule> globalRules) { this.globalRules = globalRules; }
    public Map<String, Boolean> getGlobalOverrides() { return globalOverrides; }
    public void setGlobalOverrides(Map<String, Boolean> globalOverrides) { this.globalOverrides = globalOverrides; }
    public List<NoiseRule> getCustomRules() { return customRules; }
    public void setCustomRules(List<NoiseRule> customRules) { this.customRules = customRules; }
}
