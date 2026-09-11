package com.spark.callgraph.engine.model;

/** 类/方法来源：项目自身、依赖引入、类路径上不存在的外部类 */
public enum SourceType {
    PROJECT("项目"),
    DEPENDENCY("依赖"),
    EXTERNAL("外部");

    private final String label;

    SourceType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
