package com.spark.callgraph.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 调用图中一条调用边：from(调用方) → to(被调方)，附带调用方式与行号。
 * from/to 均指向 {@link GraphMethod} 在 methods 列表中的下标。
 */
public final class GraphEdge {
    private final int from;
    private final int to;
    private final InvokeType invoke;
    private final int line;

    @JsonCreator
    public GraphEdge(@JsonProperty("from") int from,
                     @JsonProperty("to") int to,
                     @JsonProperty("invoke") InvokeType invoke,
                     @JsonProperty("line") int line) {
        this.from = from;
        this.to = to;
        this.invoke = invoke;
        this.line = line;
    }

    public int getFrom() { return from; }
    public int getTo() { return to; }
    public InvokeType getInvoke() { return invoke; }
    public int getLine() { return line; }
}