package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.List;

/** 远端仓库分支 + Tag 列表（ls-remote 结果）。 */
public class GitRefs {

    private List<String> branches = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private String defaultBranch;

    public List<String> getBranches() {
        return branches;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}
