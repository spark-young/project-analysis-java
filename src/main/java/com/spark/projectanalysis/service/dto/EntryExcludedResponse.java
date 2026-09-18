package com.spark.projectanalysis.service.dto;

/**
 * POST /api/projects/{id}/entries/exclude/batch 响应：{excluded}。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryExcludedResponse {

    private int excluded;

    public int getExcluded() { return excluded; }
    public void setExcluded(int excluded) { this.excluded = excluded; }
}
