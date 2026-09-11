package com.spark.callgraph.engine.entry;

/**
 * 业务入口方法（REST / Dubbo / ElasticJob / main 等）。
 */
public final class EntryPoint {

    private final String type;             // REST / DUBBO / ELASTIC_JOB / MAIN
    private final String className;       // 全限定类名（点分隔）
    private final String methodName;
    private final String methodDescriptor; // 重载消歧
    private final String display;         // 入口展示（如 GET /api/orders）

    public EntryPoint(String type, String className, String methodName,
                      String methodDescriptor, String display) {
        this.type = type;
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.display = display;
    }

    public String getType() { return type; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getMethodDescriptor() { return methodDescriptor; }
    public String getDisplay() { return display; }

    @Override
    public String toString() {
        return type + ":" + className + "#" + methodName + "(" + display + ")";
    }
}
