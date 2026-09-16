package com.spark.projectanalysis.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用树节点。
 */
public final class CallNode {
    private final MethodKey method;
    private final SourceType source;
    private final InvokeType invokeType; // 被调方式；根节点为 null
    private final int line;              // 调用处行号；未知为 -1
    private final List<CallNode> children = new ArrayList<>();
    private boolean cycle;               // 环：已出现在当前路径上
    private boolean truncated;           // 因深度/节点上限截断

    @JsonCreator
    public CallNode(@JsonProperty("method") MethodKey method,
                    @JsonProperty("source") SourceType source,
                    @JsonProperty("invokeType") InvokeType invokeType,
                    @JsonProperty("line") int line) {
        this.method = method;
        this.source = source;
        this.invokeType = invokeType;
        this.line = line;
    }

    public MethodKey getMethod() { return method; }
    public SourceType getSource() { return source; }
    public InvokeType getInvokeType() { return invokeType; }
    public int getLine() { return line; }
    public List<CallNode> getChildren() { return children; }
    public boolean isCycle() { return cycle; }
    public boolean isTruncated() { return truncated; }
    public boolean isLeaf() { return children.isEmpty(); }

    /** 构建期标记：环 */
    public void markCycle() { this.cycle = true; }

    /** 构建期标记：截断 */
    public void markTruncated() { this.truncated = true; }
}
