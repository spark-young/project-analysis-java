package com.spark.callgraph.service;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * JGit 克隆（纯 Java，无需服务器安装 git 命令）。
 * Token 认证：PAT 作为密码；用户名默认 github.com→token、其余（GitLab 等）→oauth2，可显式指定。
 */
@Service
public class GitCloneService {

    /**
     * 克隆仓库到目标目录。
     *
     * @param repoUrl  仓库地址（http/https/file 等）
     * @param branch   可空：默认主分支（HEAD）
     * @param token    可空：访问令牌（私有仓库必填）
     * @param username 可空：认证用户名
     * @param targetDir 目标目录（须不存在或为空目录）
     */
    public void clone(String repoUrl, String branch, String token, String username, Path targetDir)
            throws Exception {
        CloneCommand cmd = Git.cloneRepository()
                .setURI(repoUrl.trim())
                .setDirectory(targetDir.toFile());
        if (branch != null && !branch.trim().isEmpty()) {
            cmd.setBranch("refs/heads/" + branch.trim());
        }
        if (token != null && !token.trim().isEmpty()) {
            String user = username != null && !username.trim().isEmpty()
                    ? username.trim()
                    : defaultUsername(repoUrl);
            cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, token.trim()));
        }
        try (Git ignored = cmd.call()) {
            // try-with-resources 关闭仓库句柄
        }
    }

    /** Token 认证默认用户名：GitHub PAT 用户名任意（用 token）；GitLab PAT 惯例 oauth2 */
    static String defaultUsername(String url) {
        return url != null && url.contains("github.com") ? "token" : "oauth2";
    }
}
