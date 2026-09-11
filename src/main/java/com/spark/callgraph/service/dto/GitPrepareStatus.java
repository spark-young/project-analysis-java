package com.spark.callgraph.service.dto;

/** Git 准备任务状态：CLONING → COMPILING → DONE / FAILED */
public class GitPrepareStatus {
    private String jobId;
    private String status;
    private String message;
    private String projectPath;  // DONE 后可用：克隆+编译后的项目目录
    private String projectName;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
}
