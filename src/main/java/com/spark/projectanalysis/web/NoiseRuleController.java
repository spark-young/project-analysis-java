package com.spark.projectanalysis.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.projectanalysis.service.NoiseRuleService;
import com.spark.projectanalysis.service.dto.NoiseProjectDetail;
import com.spark.projectanalysis.service.dto.NoiseRule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 样板方法过滤规则管理接口（两层结构）：
 *   - 不传 projectPath → 操作全局规则
 *   - 传了 projectPath → 操作项目级规则（<项目>/.callgraph/noise-rules.json）
 *   - /merged 接口返回合并后的完整规则集（前端展示用）
 */
@RestController
@RequestMapping("/api/noise-rules")
public class NoiseRuleController {

    private final NoiseRuleService noiseRuleService;
    private final ObjectMapper objectMapper;

    public NoiseRuleController(NoiseRuleService noiseRuleService, ObjectMapper objectMapper) {
        this.noiseRuleService = noiseRuleService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取规则。
     *   无 projectPath → 全局规则
     *   有 projectPath → 项目级规则（空返回空列表）
     */
    @GetMapping
    public List<NoiseRule> list(@RequestParam(value = "projectPath", required = false) String projectPath) {
        if (projectPath != null && !projectPath.isEmpty()) {
            return noiseRuleService.getProjectRules(projectPath);
        }
        return noiseRuleService.getRules();
    }

    /**
     * 获取合并后的完整规则集（全局 + 项目）。
     * 前端在项目分析视图展示"最终生效的规则"时用这个。
     */
    @GetMapping("/merged")
    public List<NoiseRule> merged(@RequestParam(value = "projectPath", required = false) String projectPath) {
        List<NoiseRule> all = new ArrayList<>(noiseRuleService.getRules());
        if (projectPath != null && !projectPath.isEmpty()) {
            all.addAll(noiseRuleService.getProjectRules(projectPath));
        }
        return all;
    }

    /**
     * 获取项目级配置详情（新版结构）。
     * 返回 { globalRules, globalOverrides, customRules }。
     */
    @GetMapping("/project-detail")
    public NoiseProjectDetail projectDetail(@RequestParam("projectPath") String projectPath) {
        return noiseRuleService.getProjectDetail(projectPath);
    }

    /**
     * 保存规则（全量覆盖）。
     *   全局 → body: [NoiseRule, ...]
     *   项目级 → body: { globalOverrides: {}, customRules: [...] }
     */
    @PutMapping
    public Object save(@RequestBody Object body,
                       @RequestParam(value = "projectPath", required = false) String projectPath) {
        if (projectPath != null && !projectPath.isEmpty()) {
            Map<String, Object> detail = (Map<String, Object>) body;
            Map<String, Boolean> overrides = detail.get("globalOverrides") != null
                    ? (Map<String, Boolean>) detail.get("globalOverrides")
                    : null;
            List<NoiseRule> customRules = objectMapper.convertValue(detail.get("customRules"),
                    new TypeReference<List<NoiseRule>>() {});
            noiseRuleService.saveProjectDetail(projectPath, overrides, customRules);
            return noiseRuleService.getProjectDetail(projectPath);
        }
        List<NoiseRule> rules = objectMapper.convertValue(body,
                new TypeReference<List<NoiseRule>>() {});
        noiseRuleService.save(rules);
        return noiseRuleService.getRules();
    }

    /**
     * 恢复/重置规则。
     *   全局 → 恢复 8 条默认
     *   项目级 → 清空项目级规则（让它回退到只用全局）
     */
    @PostMapping("/reset")
    public List<NoiseRule> reset(@RequestParam(value = "projectPath", required = false) String projectPath) {
        if (projectPath != null && !projectPath.isEmpty()) {
            noiseRuleService.resetProjectRules(projectPath);
            return new ArrayList<>();
        }
        noiseRuleService.reset();
        return noiseRuleService.getRules();
    }

    /** 导出规则为 JSON 文件下载 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(value = "projectPath", required = false) String projectPath) throws Exception {
        List<NoiseRule> rules;
        String fileName;
        if (projectPath != null && !projectPath.isEmpty()) {
            rules = noiseRuleService.getProjectRules(projectPath);
            fileName = "noise-rules-project.json";
        } else {
            rules = noiseRuleService.getRules();
            fileName = "noise-rules.json";
        }
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(rules);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", fileName);
        return new ResponseEntity<>(json, headers, 200);
    }

    /** 从 JSON 文件导入规则（全量覆盖到指定层级） */
    @PostMapping("/import")
    public List<NoiseRule> importRules(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "projectPath", required = false) String projectPath) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<NoiseRule> rules = objectMapper.readValue(content,
                new TypeReference<List<NoiseRule>>() {});
        if (projectPath != null && !projectPath.isEmpty()) {
            noiseRuleService.saveProjectRules(projectPath, rules);
            return noiseRuleService.getProjectRules(projectPath);
        }
        noiseRuleService.save(rules);
        return noiseRuleService.getRules();
    }
}
