package com.spark.projectanalysis.service.dto;

/** 切换分支/Tag 任务状态：PENDING → FETCHING → CHECKOUT → COMPILING → DONE / FAILED */
public class SwitchStatus {

    private String jobId;
    private String status;      // PENDING / FETCHING / CHECKOUT / COMPILING / DONE / FAILED
    private String message;     // 详细消息（错误信息等）
    private String step;        // 当前步骤文字（如 "正在接收对象 45%"）
    private int progress;       // 整体进度 0-100
    private String ref;         // 目标分支名 / Tag 名
    private String refType;     // BRANCH / TAG
    private boolean stashed;    // 切换前是否自动 stash 了本地改动
    private boolean conflict;   // stash pop 是否发生冲突（stash 保留，改动未丢失）

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
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public boolean isStashed() { return stashed; }
    public void setStashed(boolean stashed) { this.stashed = stashed; }
    public boolean isConflict() { return conflict; }
    public void setConflict(boolean conflict) { this.conflict = conflict; }
}
