package com.spark.projectanalysis.service.dto;

/**
 * GET /api/projects/{id}/cache/load-file 响应。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片4）：
 * ok 恒出现；命中时带 result（无 error），未命中时带 error（无 result）。
 */
public class CacheFileResponse {

    private boolean ok;
    private String error;
    private Object result;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
}
