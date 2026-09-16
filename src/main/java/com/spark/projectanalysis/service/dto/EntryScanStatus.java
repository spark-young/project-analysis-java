package com.spark.projectanalysis.service.dto;

/**
 * 入口扫描异步任务状态快照。
 */
public class EntryScanStatus {

    public enum State { QUEUED, INDEXING, DETECTING, DONE, FAILED }

    private String jobId;
    private State state;
    private int progress;       // 0-100
    private String step;        // 当前步骤描述
    private EntryScanResult result; // DONE 时填充
    private String error;       // FAILED 时填充

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public EntryScanResult getResult() { return result; }
    public void setResult(EntryScanResult result) { this.result = result; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
