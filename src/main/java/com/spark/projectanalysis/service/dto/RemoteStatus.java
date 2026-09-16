package com.spark.projectanalysis.service.dto;

/** 本地与远端引用对比结果。 */
public class RemoteStatus {

    /** UP_TO_DATE / BEHIND / UNKNOWN */
    private String status;
    private String localSha;
    private String remoteSha;
    private String hint;
    /** 检查时间（epoch 毫秒） */
    private long checkedAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocalSha() {
        return localSha;
    }

    public void setLocalSha(String localSha) {
        this.localSha = localSha;
    }

    public String getRemoteSha() {
        return remoteSha;
    }

    public void setRemoteSha(String remoteSha) {
        this.remoteSha = remoteSha;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(long checkedAt) {
        this.checkedAt = checkedAt;
    }
}
