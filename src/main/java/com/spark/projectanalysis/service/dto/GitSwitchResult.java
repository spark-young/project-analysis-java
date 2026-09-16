package com.spark.projectanalysis.service.dto;

/** git 切换分支/Tag 的结果。 */
public class GitSwitchResult {

    private String ref;
    /** BRANCH / TAG */
    private String refType;
    /** 切换前是否自动 stash 了本地改动 */
    private boolean stashed;
    /** stash 是否已成功 pop 回工作区 */
    private boolean stashPopped;
    /** stash pop 是否发生冲突（stash 保留，改动未丢失） */
    private boolean conflict;
    private String output;

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public boolean isStashed() {
        return stashed;
    }

    public void setStashed(boolean stashed) {
        this.stashed = stashed;
    }

    public boolean isStashPopped() {
        return stashPopped;
    }

    public void setStashPopped(boolean stashPopped) {
        this.stashPopped = stashPopped;
    }

    public boolean isConflict() {
        return conflict;
    }

    public void setConflict(boolean conflict) {
        this.conflict = conflict;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
