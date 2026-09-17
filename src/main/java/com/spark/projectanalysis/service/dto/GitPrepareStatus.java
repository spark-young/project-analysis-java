package com.spark.projectanalysis.service.dto;

import java.util.List;

/** Git 准备任务状态：CLONING → DONE / FAILED */
public class GitPrepareStatus {
    private String jobId;
    private String status;      // PENDING / CLONING / DONE / FAILED
    private String message;     // 详细消息（错误信息等）
    private String step;        // 当前步骤文字（如 "正在接收对象 45%"）
    private int progress;       // 整体进度 0-100
    private String repoUrl;     // 原始仓库地址（前端刷新恢复用）
    private String projectPath; // DONE 后可用
    private String projectName;
    private List<String> compileLog;  // mvn 编译输出的最近若干行（实时滚动展示）

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public List<String> getCompileLog() { return compileLog; }
    public void setCompileLog(List<String> compileLog) { this.compileLog = compileLog; }
}