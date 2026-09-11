package com.spark.callgraph.service;

import com.spark.callgraph.service.dto.GitPrepareRequest;
import com.spark.callgraph.service.dto.GitPrepareStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Git 准备任务：克隆 → mvn 编译 → 输出可分析的项目目录。
 * 异步执行：prepare 立即返回 jobId，前端轮询 status。
 * 临时目录策略：DONE 后保留供后续扫描/分析复用；FAILED 即删；单线程执行避免并发拉取。
 */
@Service
public class GitPrepareService {

    private final GitCloneService gitCloneService;
    private final MavenCompileService mavenCompileService;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-prepare");
        t.setDaemon(true);
        return t;
    });

    static final class Job {
        final String id = UUID.randomUUID().toString();
        volatile String status = "PENDING";
        volatile String message = "";
        volatile String projectPath;
        volatile String projectName;
        volatile Path dir;
    }

    public GitPrepareService(GitCloneService gitCloneService, MavenCompileService mavenCompileService) {
        this.gitCloneService = gitCloneService;
        this.mavenCompileService = mavenCompileService;
    }

    public GitPrepareStatus prepare(GitPrepareRequest req) {
        if (req == null || req.getRepoUrl() == null || req.getRepoUrl().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "仓库地址不能为空");
        }
        Job job = new Job();
        job.projectName = repoDisplayName(req.getRepoUrl());
        jobs.put(job.id, job);

        final String repoUrl = req.getRepoUrl().trim();
        final String branch = req.getBranch();
        final String token = req.getToken();
        final String username = req.getUsername();

        executor.submit(() -> {
            job.status = "CLONING";
            job.message = "正在克隆仓库 " + repoUrl + " ...";
            try {
                Path dir = createWorkDir();
                job.dir = dir;
                gitCloneService.clone(repoUrl, branch, token, username, dir);
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = "克隆失败：" + e.getMessage();
                deleteQuietly(job.dir);
                return;
            }

            job.status = "COMPILING";
            job.message = "正在执行 mvn compile（多模块项目可能需要几分钟）...";
            try {
                MavenCompileService.CompileResult r = mavenCompileService.compile(job.dir);
                if (!r.isSuccess()) {
                    job.status = "FAILED";
                    job.message = "编译失败：\n" + r.getOutputTail();
                    deleteQuietly(job.dir);
                    return;
                }
                job.projectPath = job.dir.toString();
                job.status = "DONE";
                job.message = "";
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = "编译异常：" + e.getMessage();
                deleteQuietly(job.dir);
            }
        });
        return status(job.id);
    }

    public GitPrepareStatus status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new AnalysisException(HttpStatus.NOT_FOUND, "任务不存在或已过期: " + jobId);
        }
        GitPrepareStatus s = new GitPrepareStatus();
        s.setJobId(job.id);
        s.setStatus(job.status);
        s.setMessage(job.message);
        s.setProjectPath(job.projectPath);
        s.setProjectName(job.projectName);
        return s;
    }

    /** 仓库展示名：取 URL 末段并去掉 .git 后缀 */
    static String repoDisplayName(String repoUrl) {
        String s = repoUrl == null ? "" : repoUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int cut = Math.max(Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\')), s.lastIndexOf(':'));
        String name = cut >= 0 ? s.substring(cut + 1) : s;
        return name.isEmpty() ? "git-project" : name;
    }

    /** 克隆工作目录（测试可覆盖以定向清理） */
    protected Path createWorkDir() throws IOException {
        return Files.createTempDirectory("callgraph-git-");
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try {
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignore) {
                        // 清理失败忽略
                    }
                });
            }
        } catch (IOException ignore) {
            // 清理失败忽略
        }
    }
}
