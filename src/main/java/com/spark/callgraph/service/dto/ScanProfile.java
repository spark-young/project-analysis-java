package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描方案：一个方案 = 内置探测器开关 + 自定义规则列表。
 */
public class ScanProfile {

    private String id;
    private String name;
    private String description;
    /** 是否内置方案（内置不可编辑/删除，可复制另存） */
    private boolean builtin;

    /** 内置探测器开关：{"REST": true, "DUBBO": true, "ELASTIC_JOB": true, "MAIN": true} */
    private Map<String, Boolean> detectors = new LinkedHashMap<>();

    /** 自定义规则列表 */
    private List<ScanRule> rules = new ArrayList<>();

    public ScanProfile() {}

    /** 指定探测器是否启用（未配置默认启用，保证向后兼容） */
    public boolean isDetectorEnabled(String type) {
        if (detectors == null || !detectors.containsKey(type)) return true;
        Boolean v = detectors.get(type);
        return v == null || v;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }
    public Map<String, Boolean> getDetectors() { return detectors; }
    public void setDetectors(Map<String, Boolean> detectors) { this.detectors = detectors; }
    public List<ScanRule> getRules() { return rules; }
    public void setRules(List<ScanRule> rules) { this.rules = rules; }
}
