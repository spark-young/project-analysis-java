package com.spark.projectanalysis.service.dto;

/**
 * POST /api/projects/{id}/entries/add-batch 响应：{added, existed, list}。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryAddBatchResponse {

    private int added;
    private int existed;
    private EntryList list;

    public int getAdded() { return added; }
    public void setAdded(int added) { this.added = added; }
    public int getExisted() { return existed; }
    public void setExisted(int existed) { this.existed = existed; }
    public EntryList getList() { return list; }
    public void setList(EntryList list) { this.list = list; }
}
