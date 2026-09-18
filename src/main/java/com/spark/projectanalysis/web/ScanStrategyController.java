package com.spark.projectanalysis.web;

import com.spark.projectanalysis.service.EntryListService;
import com.spark.projectanalysis.service.EntryScanService;
import com.spark.projectanalysis.service.ProjectRegistry;
import com.spark.projectanalysis.service.ScanStrategyService;
import com.spark.projectanalysis.service.dto.EntryList.EntryItem;
import com.spark.projectanalysis.service.dto.EntryOkResponse;
import com.spark.projectanalysis.service.dto.EntryScanResult;
import com.spark.projectanalysis.service.dto.ProfileNewResponse;
import com.spark.projectanalysis.service.dto.RuleNewResponse;
import com.spark.projectanalysis.service.dto.ScanProfile;
import com.spark.projectanalysis.service.dto.ScanRule;
import com.spark.projectanalysis.service.dto.ScanStrategy;
import com.spark.projectanalysis.service.dto.ScanWithStrategyResponse;
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
    public EntryOkResponse saveGlobal(@RequestBody ScanStrategy strategy) {
        strategyService.saveGlobal(strategy);
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(true);
        return resp;
    }

    /** 恢复全局策略为默认 */
    @PostMapping("/global/reset")
    public EntryOkResponse resetGlobal() {
        strategyService.resetGlobal();
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(true);
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
    public EntryOkResponse saveProjectStrategy(@PathVariable String projectId,
                                               @RequestBody ScanStrategy strategy) {
        String path = resolvePath(projectId);
        strategyService.saveProject(path, strategy);
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(true);
        return resp;
    }

    /** 删除项目级策略（恢复全局默认） */
    @PostMapping("/project/{projectId}/reset")
    public EntryOkResponse resetProjectStrategy(@PathVariable String projectId) {
        String path = resolvePath(projectId);
        strategyService.resetProject(path);
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(true);
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
    public ScanWithStrategyResponse scanWithStrategy(@PathVariable String projectId,
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

        ScanWithStrategyResponse resp = new ScanWithStrategyResponse();
        resp.setCandidates(diff.candidates);
        resp.setExisted(diff.existed);
        resp.setScanTotalGroups(scanResult.getGroups() == null ? 0 : scanResult.getGroups().size());
        resp.setScanTotalEntries(scanResult.getGroups() == null ? 0 :
                scanResult.getGroups().stream()
                        .mapToInt(g -> g.getEntries() == null ? 0 : g.getEntries().size()).sum());
        resp.setProjectPath(path);
        resp.setProfileName(profile == null ? "未知" : profile.getName());
        log.info("[扫描策略] 项目 {} 按策略「{}」扫描完成：候选 {} 条",
                projectId, profile == null ? "未知" : profile.getName(), diff.candidates.size());
        return resp;
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 方案内新建自定义规则（生成新规则 ID） */
    @PostMapping("/rule/new")
    public RuleNewResponse newRule() {
        RuleNewResponse resp = new RuleNewResponse();
        resp.setRuleId(strategyService.newRuleId());
        return resp;
    }

    /** 新建自定义方案（生成新方案 ID） */
    @PostMapping("/profile/new")
    public ProfileNewResponse newProfile() {
        ProfileNewResponse resp = new ProfileNewResponse();
        resp.setProfileId(strategyService.newProfileId());
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