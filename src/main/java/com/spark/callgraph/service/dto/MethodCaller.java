package com.spark.callgraph.service.dto;

/** 一个方法的某个调用方（上游调用该方法的位置） */
public class MethodCaller {
    private String caller; // 调用方 method identifier
    private int line;      // 调用处行号，未知 -1

    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
}