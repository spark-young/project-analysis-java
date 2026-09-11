package com.spark.callgraph.service.dto;

/** Git 拉取+编译请求 */
public class GitPrepareRequest {
    private String repoUrl;    // 必填：http(s)/ssh/file 地址
    private String branch;    // 可空：默认主分支
    private String token;     // 可空：PAT（公开仓库可不填）
    private String username;  // 可空：默认按 URL 自动判断（github→token，其余→oauth2）

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
