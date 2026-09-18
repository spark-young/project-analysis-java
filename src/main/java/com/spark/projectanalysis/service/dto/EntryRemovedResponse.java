package com.spark.projectanalysis.service.dto;

/**
 * DELETE /api/projects/{id}/entries 响应：{removed}。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryRemovedResponse {

    private int removed;

    public int getRemoved() { return removed; }
    public void setRemoved(int removed) { this.removed = removed; }
}
