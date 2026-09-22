package com.spark.projectanalysis.service.dto;

/**
 * 一个方法的某个调用方（上游调用该方法的位置）。
 * <p>
 * callerId 为调用方在所属分析结果 {@code graph.methods} 中的下标（不再是完整签名）。
 */
public class MethodCaller {
    private int callerId;  // 调用方在 graph.methods 中的下标
    private int line;      // 调用处行号，未知 -1

    public int getCallerId() { return callerId; }
    public void setCallerId(int callerId) { this.callerId = callerId; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
}
