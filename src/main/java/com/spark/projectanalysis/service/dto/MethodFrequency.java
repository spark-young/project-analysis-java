package com.spark.projectanalysis.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个方法的被调频次统计（含调用方列表）。
 * <p>
 * methodId 为被调方法在所属分析结果 {@code graph.methods} 中的下标（不再是完整签名），
 * 前端/导出按 id 查节点表取得签名，从而大幅压缩响应体积（OPT-19）。
 */
public class MethodFrequency {
    private int methodId;    // 被调方法在 graph.methods 中的下标
    private String source;   // PROJECT / DEPENDENCY / EXTERNAL
    private int callCount;   // 被调次数（入度）
    private List<MethodCaller> callers = new ArrayList<>();

    public int getMethodId() { return methodId; }
    public void setMethodId(int methodId) { this.methodId = methodId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getCallCount() { return callCount; }
    public void setCallCount(int callCount) { this.callCount = callCount; }
    public List<MethodCaller> getCallers() { return callers; }
    public void setCallers(List<MethodCaller> callers) { this.callers = callers; }
}
