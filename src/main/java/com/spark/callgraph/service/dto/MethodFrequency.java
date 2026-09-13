package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.List;

/** 单个方法的被调频次统计（含调用方列表） */
public class MethodFrequency {
    private String method;   // MethodKey.getIdentifier()，如 com.foo.Bar#pay(String)
    private String source;   // PROJECT / DEPENDENCY / EXTERNAL
    private int callCount;   // 被调次数（入度）
    private List<MethodCaller> callers = new ArrayList<>();

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getCallCount() { return callCount; }
    public void setCallCount(int callCount) { this.callCount = callCount; }
    public List<MethodCaller> getCallers() { return callers; }
    public void setCallers(List<MethodCaller> callers) { this.callers = callers; }
}