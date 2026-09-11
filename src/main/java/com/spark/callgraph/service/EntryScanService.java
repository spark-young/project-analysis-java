package com.spark.callgraph.service;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.entry.EntryPoint;
import com.spark.callgraph.engine.entry.EntryPointDetector;
import com.spark.callgraph.service.dto.EntryScanResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入口扫描编排：构建注册表（复用缓存）→ 运行全部探测器 → 按类型分组。
 */
@Service
public class EntryScanService {

    /** 分组展示顺序 */
    private static final List<String> TYPE_ORDER =
            Arrays.asList("REST", "DUBBO", "ELASTIC_JOB", "MAIN");

    private final AnalysisService analysisService;
    private final List<EntryPointDetector> detectors;

    public EntryScanService(AnalysisService analysisService, List<EntryPointDetector> detectors) {
        this.analysisService = analysisService;
        this.detectors = detectors;
    }

    public EntryScanResult scan(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不能为空");
        }
        if (!Files.exists(Paths.get(projectPath.trim()))) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不存在: " + projectPath);
        }

        AnalysisService.RegistryHandle handle = analysisService.registryFor(projectPath.trim());
        ClassMetadataRegistry registry = handle.getRegistry();

        Map<String, List<EntryPoint>> grouped = new LinkedHashMap<>();
        for (EntryPointDetector detector : detectors) {
            List<EntryPoint> found = detector.detect(registry);
            if (!found.isEmpty()) {
                grouped.put(detector.type(), found);
            }
        }

        EntryScanResult result = new EntryScanResult();
        result.setProjectPath(projectPath.trim());
        result.setProjectName(handle.getProjectName());
        List<EntryScanResult.Group> groups = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted((a, b) -> Integer.compare(order(a.getKey()), order(b.getKey())))
                .forEach(e -> {
                    EntryScanResult.Group g = new EntryScanResult.Group();
                    g.setType(e.getKey());
                    g.setLabel(labelOf(e.getKey()));
                    for (EntryPoint p : e.getValue()) {
                        EntryScanResult.EntryDto dto = new EntryScanResult.EntryDto();
                        dto.setClassName(p.getClassName());
                        dto.setMethodName(p.getMethodName());
                        dto.setMethodDescriptor(p.getMethodDescriptor());
                        dto.setDisplay(p.getDisplay());
                        g.getEntries().add(dto);
                    }
                    groups.add(g);
                });
        result.setGroups(groups);
        return result;
    }

    private static int order(String type) {
        int i = TYPE_ORDER.indexOf(type);
        return i < 0 ? TYPE_ORDER.size() : i;
    }

    private String labelOf(String type) {
        for (EntryPointDetector d : detectors) {
            if (d.type().equals(type)) return d.label();
        }
        return type;
    }
}
