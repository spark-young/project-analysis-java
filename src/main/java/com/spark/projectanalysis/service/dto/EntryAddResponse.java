package com.spark.projectanalysis.service.dto;

/**
 * POST /api/projects/{id}/entries/add 响应：{added, list}。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryAddResponse {

    private boolean added;
    private EntryList list;

    public boolean isAdded() { return added; }
    public void setAdded(boolean added) { this.added = added; }
    public EntryList getList() { return list; }
    public void setList(EntryList list) { this.list = list; }
}
