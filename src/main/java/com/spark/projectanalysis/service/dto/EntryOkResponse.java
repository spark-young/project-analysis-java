package com.spark.projectanalysis.service.dto;

/**
 * POST /api/projects/{id}/entries/exclude 与 /entries/restore 响应：{ok}。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片2）。
 */
public class EntryOkResponse {

    private boolean ok;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
}
