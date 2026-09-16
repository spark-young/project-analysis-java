package com.spark.projectanalysis.engine.model;

/** 字节码调用指令类型 */
public enum InvokeType {
    VIRTUAL("虚调用"),
    STATIC("静态"),
    INTERFACE("接口"),
    SPECIAL("构造/super"),
    DYNAMIC("lambda/方法引用"),
    IMPL("接口实现分派");

    private final String label;

    InvokeType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
