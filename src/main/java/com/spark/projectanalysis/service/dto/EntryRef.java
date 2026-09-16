package com.spark.projectanalysis.service.dto;

/** 多入口分析时的单个入口引用 */
public class EntryRef {
    private String className;
    private String methodName;
    private String methodDescriptor; // 可空：用于重载消歧

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getMethodDescriptor() { return methodDescriptor; }
    public void setMethodDescriptor(String methodDescriptor) { this.methodDescriptor = methodDescriptor; }
}
