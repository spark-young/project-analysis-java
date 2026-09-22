package com.spark.projectanalysis.service.dto;

/**
 * POST /api/analyze/batch 响应：{jobId}。
 * 字段与原 Map<String,String> 版本逐字一致（OPT-28 切片5）。
 */
public class BatchJobStartResponse {

    private String jobId;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
}
