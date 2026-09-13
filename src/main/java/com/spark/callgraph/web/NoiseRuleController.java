package com.spark.callgraph.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.callgraph.service.NoiseRuleService;
import com.spark.callgraph.service.dto.NoiseRule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 样板方法过滤规则管理接口。
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

    /** 获取所有规则 */
    @GetMapping
    public List<NoiseRule> list() {
        return noiseRuleService.getRules();
    }

    /** 保存规则（全量覆盖） */
    @PutMapping
    public List<NoiseRule> save(@RequestBody List<NoiseRule> rules) {
        noiseRuleService.save(rules);
        return noiseRuleService.getRules();
    }

    /** 恢复默认规则 */
    @PostMapping("/reset")
    public List<NoiseRule> reset() {
        noiseRuleService.reset();
        return noiseRuleService.getRules();
    }

    /** 导出规则为 JSON 文件下载 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws Exception {
        List<NoiseRule> rules = noiseRuleService.getRules();
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(rules);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "noise-rules.json");
        return new ResponseEntity<>(json, headers, 200);
    }

    /** 从 JSON 文件导入规则（全量覆盖） */
    @PostMapping("/import")
    public List<NoiseRule> importRules(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<NoiseRule> rules = objectMapper.readValue(content,
                new TypeReference<List<NoiseRule>>() {});
        noiseRuleService.save(rules);
        return noiseRuleService.getRules();
    }
}
