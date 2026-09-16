package com.spark.projectanalysis.web;

import com.spark.projectanalysis.service.EntryListService;
import com.spark.projectanalysis.service.EntryScanService;
import com.spark.projectanalysis.service.ProjectRegistry;
import com.spark.projectanalysis.service.ScanStrategyService;
import com.spark.projectanalysis.service.dto.EntryList.EntryItem;
import com.spark.projectanalysis.service.dto.EntryScanResult;
import com.spark.projectanalysis.service.dto.ScanProfile;
import com.spark.projectanalysis.service.dto.ScanRule;
import com.spark.projectanalysis.service.dto.ScanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描策略配置 REST 接口。
 * <p>
 * 提供全局和项目级策略的读取、保存，以及策略驱动的入口扫描。
 */
@RestController
@RequestMapping("/api/scan-strategy")
public class ScanStrategyController {

    private static final Logger log = LoggerFactory.getLogger(ScanStrategyController.class);

    private final ScanStrategyService strategyService;
    private final EntryScanService entryScanService;
    private final EntryListService entryListService;
    private final ProjectRegistry registry;

    public ScanStrategyController(ScanStrategyService strategyService,
                                  EntryScanService entryScanService,
                                  EntryListService entryListService,
                                  ProjectRegistry registry) {
        this.strategyService = strategyService;
        this.entryScanService = entryScanService;
        this.entryListService = entryListService;
        this.registry = registry;
    }

    // ================================================================
    // 全局策略
    // ================================================================

    /** 取全局扫描策略 */
    @GetMapping("/global")
    public ScanStrategy getGlobal() {
        return strategyService.getGlobal();
    }

    /** 保存全局扫描策略 */
    @PutMapping("/global")
    public Map<String, Object> saveGlobal(@RequestBody ScanStrategy strategy) {
        strategyService.saveGlobal(strategy);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    /** 恢复全局策略为默认 */
    @PostMapping("/global/reset")
    public Map<String, Object> resetGlobal() {
        strategyService.resetGlobal();
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    // ================================================================
    // 项目级策略
    // ================================================================

    /** 取项目生效策略（全局 + 项目级合并） */
    @GetMapping("/project/{projectId}")
    public ScanStrategy getProjectStrategy(@PathVariable String projectId) {
        String path = resolvePath(projectId);
        return strategyService.effectiveForProject(path);
    }

    /** 保存项目级策略 */
    @PutMapping("/project/{projectId}")
    public Map<String, Object> saveProjectStrategy(@PathVariable String projectId,
                                                    @RequestBody ScanStrategy strategy) {
        String path = resolvePath(projectId);
        strategyService.saveProject(path, strategy);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    /** 删除项目级策略（恢复全局默认） */
    @PostMapping("/project/{projectId}/reset")
    public Map<String, Object> resetProjectStrategy(@PathVariable String projectId) {
        String path = resolvePath(projectId);
        strategyService.resetProject(path);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    // ================================================================
    // 可用方案列表
    // ================================================================

    /** 取可用方案列表（供前端下拉使用） */
    @GetMapping("/profiles")
    public List<ScanProfile> getProfiles() {
        ScanStrategy global = strategyService.getGlobal();
        return global == null ? List.of() : global.getProfiles();
    }

    // ================================================================
    // 策略驱动入口扫描（替代 /api/projects/{id}/entries/scan）
    // ================================================================

    /**
     * 按策略扫描入口并 diff 候选。
     *
     * @param projectId      项目 ID
     * @param body           请求体，可选字段：
     *                       profileId — 指定方案 ID（不传则用当前激活方案）
     */
    @PostMapping("/scan/{projectId}")
    public Map<String, Object> scanWithStrategy(@PathVariable String projectId,
                                                 @RequestBody Map<String, String> body) {
        String path = resolvePath(projectId);
        String profileId = body == null ? null : body.get("profileId");

        EntryScanResult scanResult;
        if (profileId != null && !profileId.isEmpty()) {
            scanResult = entryScanService.scan(path, profileId);
        } else {
            scanResult = entryScanService.scan(path);
        }

        EntryListService.ScanDiffResult diff = entryListService.diffScanCandidates(path, scanResult);

        // 获取当前生效策略名
        ScanStrategy strategy = profileId != null && !profileId.isEmpty()
                ? strategyService.effectiveForProject(path)
                : strategyService.effectiveForProject(path);
        ScanProfile profile = profileId != null ? strategy.profile(profileId) : strategy.activeProfile();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("candidates", diff.candidates);
        resp.put("existed", diff.existed);
        resp.put("scanTotalGroups", scanResult.getGroups() == null ? 0 : scanResult.getGroups().size());
        resp.put("scanTotalEntries", scanResult.getGroups() == null ? 0 :
                scanResult.getGroups().stream()
                        .mapToInt(g -> g.getEntries() == null ? 0 : g.getEntries().size()).sum());
        resp.put("projectPath", path);
        resp.put("profileName", profile == null ? "未知" : profile.getName());
        log.info("[扫描策略] 项目 {} 按策略「{}」扫描完成：候选 {} 条",
                projectId, profile == null ? "未知" : profile.getName(), diff.candidates.size());
        return resp;
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 方案内新建自定义规则（生成新规则 ID） */
    @PostMapping("/rule/new")
    public Map<String, String> newRule() {
        Map<String, String> resp = new HashMap<>();
        resp.put("ruleId", strategyService.newRuleId());
        return resp;
    }

    /** 新建自定义方案（生成新方案 ID） */
    @PostMapping("/profile/new")
    public Map<String, String> newProfile() {
        Map<String, String> resp = new HashMap<>();
        resp.put("profileId", strategyService.newProfileId());
        return resp;
    }

    private String resolvePath(String projectId) {
        ProjectRegistry.RegisteredProject p = registry.get(projectId);
        if (p == null) {
            throw new RuntimeException("项目不存在: " + projectId);
        }
        return p.projectPath;
    }
}