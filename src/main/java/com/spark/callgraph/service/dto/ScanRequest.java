package com.spark.callgraph.service.dto;

/** 入口扫描请求 */
public class ScanRequest {
    private String projectPath;

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
}
