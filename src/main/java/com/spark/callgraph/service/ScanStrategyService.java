package com.spark.callgraph.service;

import com.spark.callgraph.config.CallgraphPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spark.callgraph.service.dto.ScanProfile;
import com.spark.callgraph.service.dto.ScanRule;
import com.spark.callgraph.service.dto.ScanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 扫描策略管理（两层结构）：
 *
 *   ┌─ 全局层：D:\.callgraph\scan-strategy.json ────────┐
 *   │  内置方案（标准/纯API/定时任务）+ 用户全局自定义方案 │
 *   │  首次启动自动生成内置方案                           │
 *   └──────────────────────────────────────────────────┘
 *                     + 覆盖生效
 *   ┌─ 项目层：<项目>/.callgraph/scan-strategy.json ────┐
 *   │  项目专属策略，存在即覆盖全局的 activeProfileId    │
 *   │  与 profiles 数组                                 │
 *   └──────────────────────────────────────────────────┘
 */
@Service
public class ScanStrategyService {

    private static final Logger log = LoggerFactory.getLogger(ScanStrategyService.class);

    private static final String FILE_NAME = "scan-strategy.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 全局策略（内存缓存，懒加载） */
    private volatile ScanStrategy global;

    public ScanStrategyService() {
        loadGlobal();
    }

    // ================================================================
    // 全局层
    // ================================================================

    /** 全局策略文件路径 */
    public static Path globalFile() {
        return CallgraphPaths.getHome().resolve(FILE_NAME);
    }

    /** 项目级策略文件路径 */
    public static Path projectFile(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return null;
        return Paths.get(projectPath, ".callgraph", FILE_NAME);
    }

    /** 从文件加载全局策略；文件不存在则生成内置方案并保存 */
    public synchronized void loadGlobal() {
        Path file = globalFile();
        if (Files.exists(file)) {
            try {
                ScanStrategy loaded = mapper.readValue(file.toFile(), ScanStrategy.class);
                if (loaded != null) {
                    normalizeBuiltins(loaded);
                    global = loaded;
                    return;
                }
            } catch (IOException e) {
                log.warn("[扫描策略] 全局策略文件损坏，重建默认: {}", e.getMessage());
            }
        }
        global = defaultStrategy();
        saveQuietly(file, global);
    }

    /** 获取全局策略（返回引用，调用方不应直接改字段） */
    public ScanStrategy getGlobal() {
        if (global == null) loadGlobal();
        return global;
    }

    /** 保存全局策略（全量覆盖，文件损坏时重建内置方案） */
    public synchronized void saveGlobal(ScanStrategy strategy) {
        if (strategy == null) return;
        normalizeBuiltins(strategy);
        global = strategy;
        saveQuietly(globalFile(), global);
    }

    /** 恢复全局策略为默认（内置方案） */
    public synchronized void resetGlobal() {
        global = defaultStrategy();
        saveQuietly(globalFile(), global);
    }

    // ================================================================
    // 项目级
    // ================================================================

    /**
     * 获取项目生效策略：项目级文件存在 → 覆盖全局的 activeProfileId + profiles；
     * 不存在 → 直接用全局。返回副本，调用方安全修改。
     */
    public synchronized ScanStrategy effectiveForProject(String projectPath) {
        ScanStrategy base = getGlobal();
        ScanStrategy copy = deepCopy(base);

        Path file = projectFile(projectPath);
        if (file == null || !Files.exists(file)) return copy;

        try {
            ScanStrategy project = mapper.readValue(file.toFile(), ScanStrategy.class);
            if (project != null) {
                if (project.getActiveProfileId() != null && !project.getActiveProfileId().isEmpty()) {
                    copy.setActiveProfileId(project.getActiveProfileId());
                }
                if (project.getProfiles() != null && !project.getProfiles().isEmpty()) {
                    // 项目级方案替换 profiles：内置方案从全局补全，保证引用有效
                    List<ScanProfile> merged = new ArrayList<>();
                    for (ScanProfile gp : base.getProfiles()) {
                        if (gp.isBuiltin()) merged.add(gp);
                    }
                    for (ScanProfile pp : project.getProfiles()) {
                        if (!pp.isBuiltin()) merged.add(pp);
                    }
                    // 若项目指定了自定义方案但 profiles 里没有 → 用全局的对应方案
                    if (copy.profile(copy.getActiveProfileId()) == null) {
                        ScanProfile fallback = base.profile(copy.getActiveProfileId());
                        if (fallback != null) merged.add(fallback);
                    }
                    copy.setProfiles(merged);
                }
            }
        } catch (IOException e) {
            log.warn("[扫描策略] 项目级策略读取失败 {}: {}", file, e.getMessage());
        }
        return copy;
    }

    /** 保存项目级策略（全量覆盖） */
    public synchronized void saveProject(String projectPath, ScanStrategy strategy) {
        if (projectPath == null || projectPath.isEmpty()) return;
        if (strategy == null) return;
        Path file = projectFile(projectPath);
        if (file == null) return;
        // 项目级只存自定义方案，内置方案跟随全局
        ScanStrategy toSave = new ScanStrategy();
        toSave.setActiveProfileId(strategy.getActiveProfileId());
        List<ScanProfile> custom = new ArrayList<>();
        for (ScanProfile p : strategy.getProfiles()) {
            if (!p.isBuiltin()) custom.add(p);
        }
        toSave.setProfiles(custom);
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), toSave);
            log.info("[扫描策略] 项目级策略已写入 {} ({} 个自定义方案)", file, custom.size());
        } catch (IOException e) {
            log.warn("[扫描策略] 项目级策略保存失败 {}: {}", file, e.getMessage());
        }
    }

    /** 删除项目级策略（恢复全局默认） */
    public synchronized void resetProject(String projectPath) {
        Path file = projectFile(projectPath);
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
            log.info("[扫描策略] 项目级策略已删除 {}", file);
        } catch (IOException e) {
            log.warn("[扫描策略] 项目级策略删除失败 {}: {}", file, e.getMessage());
        }
    }

    /** 判断项目是否有项目级策略文件 */
    public boolean hasProjectStrategy(String projectPath) {
        Path file = projectFile(projectPath);
        return file != null && Files.exists(file);
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 生成默认策略（3 个内置方案） */
    private static ScanStrategy defaultStrategy() {
        ScanStrategy s = new ScanStrategy();
        s.setActiveProfileId(ScanStrategy.BUILTIN_STANDARD);
        s.setProfiles(Arrays.asList(
                builtinStandard(),
                builtinApiOnly(),
                builtinJobOnly()
        ));
        return s;
    }

    private static ScanProfile builtinStandard() {
        ScanProfile p = new ScanProfile();
        p.setId(ScanStrategy.BUILTIN_STANDARD);
        p.setName("标准扫描");
        p.setDescription("扫描所有常见交易入口（REST/Dubbo/定时任务/Main）");
        p.setBuiltin(true);
        p.setDetectors(allDetectors(true));
        p.setRules(new ArrayList<>());
        return p;
    }

    private static ScanProfile builtinApiOnly() {
        ScanProfile p = new ScanProfile();
        p.setId(ScanStrategy.BUILTIN_API_ONLY);
        p.setName("纯 API 扫描");
        p.setDescription("仅扫描 HTTP REST 接口");
        p.setBuiltin(true);
        Map<String, Boolean> det = allDetectors(false);
        det.put("REST", true);
        p.setDetectors(det);
        p.setRules(new ArrayList<>());
        return p;
    }

    private static ScanProfile builtinJobOnly() {
        ScanProfile p = new ScanProfile();
        p.setId(ScanStrategy.BUILTIN_JOB_ONLY);
        p.setName("定时任务扫描");
        p.setDescription("仅扫描定时任务入口");
        p.setBuiltin(true);
        Map<String, Boolean> det = allDetectors(false);
        det.put("ELASTIC_JOB", true);
        p.setDetectors(det);
        p.setRules(new ArrayList<>());
        return p;
    }

    private static Map<String, Boolean> allDetectors(boolean enabled) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("REST", enabled);
        m.put("DUBBO", enabled);
        m.put("ELASTIC_JOB", enabled);
        m.put("MAIN", enabled);
        return m;
    }

    /** 确保内置方案始终存在（用户删文件或文件被改动时兜底） */
    private static void normalizeBuiltins(ScanStrategy s) {
        if (s.getProfiles() == null) s.setProfiles(new ArrayList<>());
        ensureBuiltin(s, builtinStandard());
        ensureBuiltin(s, builtinApiOnly());
        ensureBuiltin(s, builtinJobOnly());
    }

    private static void ensureBuiltin(ScanStrategy s, ScanProfile builtin) {
        if (s.profile(builtin.getId()) == null) {
            s.getProfiles().add(builtin);
        }
    }

    /** 深拷贝（避免上层误改内存全局策略） */
    private ScanStrategy deepCopy(ScanStrategy src) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(src);
            return mapper.readValue(bytes, ScanStrategy.class);
        } catch (IOException e) {
            log.warn("[扫描策略] 深拷贝失败，回退原引用: {}", e.getMessage());
            return src;
        }
    }

    private void saveQuietly(Path file, ScanStrategy strategy) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), strategy);
        } catch (IOException ignored) {
            // 保存失败不影响运行（内存策略仍生效）
        }
    }

    /** 生成新方案 ID */
    public String newProfileId() {
        return "profile-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 生成新规则 ID */
    public String newRuleId() {
        return "rule-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
