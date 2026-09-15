package com.spark.callgraph.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spark.callgraph.config.CallgraphPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 项目注册表：持久化管理导入过的项目（本地 / Git）。
 * 存于 D:\.callgraph\projects.json。
 */
@Service
public class ProjectRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProjectRegistry.class);

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** 注册表文件路径 */
    private Path registryFile() {
        return CallgraphPaths.getHome().resolve("projects.json");
    }

    /** 读取所有项目（按最近打开时间降序） */
    public List<RegisteredProject> list() {
        Path file = registryFile();
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try {
            List<RegisteredProject> items = mapper.readValue(file.toFile(),
                    new TypeReference<List<RegisteredProject>>() {});
            items.sort(Comparator.comparingLong(RegisteredProject::getLastOpenedAt).reversed());
            return items;
        } catch (IOException e) {
            log.warn("[项目注册表] 读取失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 按 id 读取单个项目 */
    public RegisteredProject get(String id) {
        return list().stream().filter(p -> p.id.equals(id)).findFirst().orElse(null);
    }

    /** 按路径查找（本地 = path，Git = workdir） */
    public RegisteredProject getByPath(String path) {
        if (path == null) return null;
        return list().stream()
                .filter(p -> path.equals(p.projectPath))
                .findFirst().orElse(null);
    }

    /** 新增或更新（按 id 覆盖） */
    public synchronized RegisteredProject save(RegisteredProject project) {
        List<RegisteredProject> all = list();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(project.id)) { idx = i; break; }
        }
        if (idx >= 0) all.set(idx, project); else all.add(project);
        writeAll(all);
        return project;
    }

    /** 记录一次"打开/分析"，更新 lastOpenedAt */
    public void touch(String id) {
        RegisteredProject p = get(id);
        if (p != null) {
            p.lastOpenedAt = System.currentTimeMillis();
            save(p);
        }
    }

    /** 删除 */
    public synchronized void delete(String id) {
        List<RegisteredProject> all = list();
        all.removeIf(p -> p.id.equals(id));
        writeAll(all);
    }

    private void writeAll(List<RegisteredProject> all) {
        try {
            Files.createDirectories(registryFile().getParent());
            mapper.writeValue(registryFile().toFile(), all);
        } catch (IOException e) {
            log.warn("[项目注册表] 写入失败: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    public static class RegisteredProject {
        public String id;            // UUID
        public String name;          // 显示名（项目目录名 / Git 仓库名）
        public String type;          // "LOCAL" / "GIT"
        public String projectPath;   // 本地路径 / Git 工作目录
        public String gitUrl;        // Git 项目：仓库地址
        public String gitBranch;     // Git 项目：分支（可空）
        public long lastOpenedAt;    // 最近打开时间戳
        public long createdAt;       // 注册时间戳
        public String lastError;     // 最后一次准备失败时的错误信息（可空）

        // === 下面是运行时状态字段（也会存到 projects.json，不影响）===
        public String changeStatus;  // "UP_TO_DATE" / "NEEDS_COMPILE" / "NEEDS_ANALYZE" / "MISSING"
        public String changeHint;    // 人类可读提示："源码已修改，需重新编译" 等
        public boolean compiled;     // 是否有编译产物
        public boolean analyzed;     // 是否有分析缓存
        public boolean existsOnDisk;  // 磁盘目录是否存在

        // === Git 分支/Tag 切换与远端更新检测字段（向后兼容，旧 JSON 反序列化为 null）===
        public String gitToken;              // 认证 token（方案 A：持久化复用）
        public String gitUsername;           // 配套用户名
        public String currentRef;            // 当前所在分支名或 Tag 名
        public String currentRefType;        // "BRANCH" / "TAG"
        public String remoteUpdateStatus;    // "UP_TO_DATE" / "BEHIND" / "UNKNOWN"
        public Long lastCheckTime;           // 最近一次远端检查时间（epoch 毫秒）

        public RegisteredProject() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
        public String getGitUrl() { return gitUrl; }
        public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }
        public String getGitBranch() { return gitBranch; }
        public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }
        public long getLastOpenedAt() { return lastOpenedAt; }
        public void setLastOpenedAt(long lastOpenedAt) { this.lastOpenedAt = lastOpenedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }

        public String getGitToken() { return gitToken; }
        public void setGitToken(String gitToken) { this.gitToken = gitToken; }
        public String getGitUsername() { return gitUsername; }
        public void setGitUsername(String gitUsername) { this.gitUsername = gitUsername; }
        public String getCurrentRef() { return currentRef; }
        public void setCurrentRef(String currentRef) { this.currentRef = currentRef; }
        public String getCurrentRefType() { return currentRefType; }
        public void setCurrentRefType(String currentRefType) { this.currentRefType = currentRefType; }
        public String getRemoteUpdateStatus() { return remoteUpdateStatus; }
        public void setRemoteUpdateStatus(String remoteUpdateStatus) { this.remoteUpdateStatus = remoteUpdateStatus; }
        public Long getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(Long lastCheckTime) { this.lastCheckTime = lastCheckTime; }
    }
}
