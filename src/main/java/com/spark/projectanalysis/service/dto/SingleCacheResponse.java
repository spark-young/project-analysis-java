package com.spark.projectanalysis.service.dto;

/**
 * GET /api/projects/{id}/cache/load-single 响应。
 * 字段与原 Map<String,Object> 版本逐字一致（OPT-28 切片4）：
 * currentEntryCount/hasCache 恒出现；dirty/cachedEntryCount/analyzedAt/result
 * 仅在 hasCache=true 时出现（DTO null 字段经 non_null 省略，等价于原 Map 不含该键）。
 * result 为透传的缓存内容（任意 JSON 对象），保持 Object 类型以兼容历史缓存格式。
 */
public class SingleCacheResponse {

    private int currentEntryCount;
    private boolean hasCache;
    private Boolean dirty;
    private Integer cachedEntryCount;
    private Long analyzedAt;
    private Object result;

    public int getCurrentEntryCount() { return currentEntryCount; }
    public void setCurrentEntryCount(int currentEntryCount) { this.currentEntryCount = currentEntryCount; }
    public boolean isHasCache() { return hasCache; }
    public void setHasCache(boolean hasCache) { this.hasCache = hasCache; }
    public Boolean getDirty() { return dirty; }
    public void setDirty(Boolean dirty) { this.dirty = dirty; }
    public Integer getCachedEntryCount() { return cachedEntryCount; }
    public void setCachedEntryCount(Integer cachedEntryCount) { this.cachedEntryCount = cachedEntryCount; }
    public Long getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Long analyzedAt) { this.analyzedAt = analyzedAt; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
}
