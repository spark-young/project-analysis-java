package com.spark.projectanalysis.service.dto;

/**
 * POST /api/scan-strategy/rule/new 响应：{ruleId}。
 * 字段与原 Map<String,String> 版本逐字一致（OPT-28 切片3）。
 */
public class RuleNewResponse {

    private String ruleId;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
}
