package com.spark.callgraph.web;

import com.spark.callgraph.service.AnalysisException;
import com.spark.callgraph.service.AnalysisService;
import com.spark.callgraph.service.ProjectRegistry;
import com.spark.callgraph.service.ProjectRegistry.RegisteredProject;
import com.spark.callgraph.service.dto.ProjectInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 项目注册表 REST 接口。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private final ProjectRegistry registry;
    private final AnalysisService analysisService;

    public ProjectsController(ProjectRegistry registry, AnalysisService analysisService) {
        this.registry = registry;
        this.analysisService = analysisService;
    }

    /** 列出所有项目 */
    @GetMapping
    public List<RegisteredProject> list() {
        return registry.list();
    }

    /** 注册本地项目（直接加进列表，不做编译） */
    @PostMapping("/local")
    public RegisteredProject registerLocal(@RequestBody LocalRegisterRequest req) {
        if (req.path == null || req.path.isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "请填写项目路径");
        }
        ProjectInfo info;
        try {
            info = analysisService.projectInfo(req.path);
        } catch (Exception e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "无法识别该路径为有效 Java 项目：" + e.getMessage());
        }
        RegisteredProject existing = registry.getByPath(req.path);
        if (existing != null) {
            existing.lastOpenedAt = System.currentTimeMillis();
            registry.save(existing);
            return existing;
        }
        RegisteredProject p = new RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = req.name != null && !req.name.isEmpty() ? req.name : info.getName();
        p.type = "LOCAL";
        p.projectPath = req.path;
        p.createdAt = System.currentTimeMillis();
        p.lastOpenedAt = p.createdAt;
        return registry.save(p);
    }

    /** 打开项目（更新 lastOpenedAt） */
    @PostMapping("/{id}/open")
    public RegisteredProject open(@PathVariable String id) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        p.lastOpenedAt = System.currentTimeMillis();
        registry.save(p);
        return p;
    }

    /** 删除项目（只从注册表移除，不删除磁盘上的工作目录） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (registry.get(id) == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        registry.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 重命名项目 */
    @PutMapping("/{id}")
    public RegisteredProject rename(@PathVariable String id, @RequestBody RenameRequest req) {
        RegisteredProject p = registry.get(id);
        if (p == null) throw new AnalysisException(HttpStatus.NOT_FOUND, "项目不存在");
        if (req.name != null && !req.name.trim().isEmpty()) {
            p.name = req.name.trim();
        }
        return registry.save(p);
    }

    public static class LocalRegisterRequest {
        public String path;
        public String name;
    }

    public static class RenameRequest {
        public String name;
    }
}
