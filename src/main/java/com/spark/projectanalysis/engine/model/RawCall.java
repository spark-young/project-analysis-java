package com.spark.projectanalysis.engine.model;

/**
 * 方法体内提取到的一条原始调用指令（未解析）。
 */
public final class RawCall {
    private final String owner;
    private final String name;
    private final String descriptor;
    private final InvokeType invokeType;
    private final int line;

    public RawCall(String owner, String name, String descriptor, InvokeType invokeType, int line) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.invokeType = invokeType;
        this.line = line;
    }

    public String getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescriptor() { return descriptor; }
    public InvokeType getInvokeType() { return invokeType; }
    public int getLine() { return line; }
}
