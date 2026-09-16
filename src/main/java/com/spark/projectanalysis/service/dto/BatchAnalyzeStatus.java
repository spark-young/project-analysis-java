package com.spark.projectanalysis.service.dto;

/**
 * 批量分析（按交易入口清单）异步任务状态快照。
 */
public class BatchAnalyzeStatus {

    public enum State { QUEUED, INDEXING, ANALYZING, DONE, FAILED }

    private String jobId;
    private State state;
    private int progress;       // 0-100
    private String step;        // 当前步骤描述
    private int done;           // 已完成入口数
    private int total;          // 总入口数
    private String error;       // FAILED 时填充

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public int getDone() { return done; }
    public void setDone(int done) { this.done = done; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
