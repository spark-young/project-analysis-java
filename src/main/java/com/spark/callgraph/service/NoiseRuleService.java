package com.spark.callgraph.service;

import com.spark.callgraph.config.CallgraphPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.callgraph.service.dto.NoiseRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 样板方法过滤规则管理（两层结构）：
 *
 *   ┌─ 全局层：D:\.callgraph\noise-rules.json ─────────┐
 *   │  通用 Java/Spring 噪声（构造器/getter/setter/日志） │
 *   │  首次启动自动生成 8 条默认规则                      │
 *   └──────────────────────────────────────────────────┘
 *                    + 合并生效
 *   ┌─ 项目层：<项目>/.callgraph/noise-rules.json ─────┐
 *   │  项目特有的噪声规则（可空）                         │
 *   │  存在时与全局层**合并**生效（任一命中即算噪声）      │
 *   └──────────────────────────────────────────────────┘
 *
 * 判断逻辑：isNoise(method) = isNoiseOn(全局规则) || isNoiseOn(项目规则)
 */
@Service
public class NoiseRuleService {

    private static final Logger log = LoggerFactory.getLogger(NoiseRuleService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 全局规则文件路径 */
    private static final Path GLOBAL_RULES_FILE = CallgraphPaths.noiseRulesFile();

    /** 默认规则集 */
    private static final List<NoiseRule> DEFAULT_RULES = Arrays.asList(
            new NoiseRule("factory", "单例/工厂",
                    "getInstance|getBean|getBeanFactory|getInstance.*", "", "ALL", true),
            new NoiseRule("constructor", "构造器",
                    "<init>", "", "ALL", true),
            new NoiseRule("object-method", "Object 方法",
                    "toString|equals|hashCode|getClass|clone|finalize", "", "ALL", true),
            new NoiseRule("logging", "日志",
                    "(info|debug|error|warn|trace|fatal)", ".*(Logger|Log|Slf4j).*", "ALL", true),
            new NoiseRule("lifecycle", "Spring 生命周期",
                    "afterPropertiesSet|initMethod|destroy|initialize|dispose|onApplicationEvent|postProcess.*",
                    "", "ALL", true),
            // getter：仅匹配 0 个参数的 getXxx/isXxx，避免误杀 getUserById 等带参业务方法
            new NoiseRule("simple-getter", "简单 getter（0 参数，getXxx/isXxx）",
                    "^(get|is)[A-Z]", "", "PROJECT", 0, true),
            // setter：仅匹配 1 个参数的 setXxx
            new NoiseRule("simple-setter", "简单 setter（1 参数，setXxx）",
                    "^set[A-Z]", "", "PROJECT", 1, true)
    );

    /** 内存中的全局规则（从文件加载或默认） */
    private volatile List<NoiseRule> globalRules = new ArrayList<>(DEFAULT_RULES);

    /**
     * 项目级规则集内存缓存：key = projectPath（null/空 → ""）。
     * 目的：isNoise/getMatchedRule 是导出热路径，原来每次调用都要读盘 + JSON 解析（×2），
     * 这里改为内存命中，仅在文件变化或写入后重建。
     */
    private final Map<String, ProjectRuleSet> projectRuleCache = new ConcurrentHashMap<>();

    /** 文件元信息复查间隔（毫秒）：窗口内直接走内存，避免热路径反复 stat 磁盘 */
    private static final long META_RECHECK_INTERVAL_MS = 2000L;

    /** 某项目的规则集快照：解析结果 + 预先把覆盖套到全局规则上的"生效全局规则" */
    private static final class ProjectRuleSet {
        final long mtime;
        final long size;
        volatile long checkedAtMs;
        final Map<String, Boolean> overrides;
        final List<NoiseRule> customRules;
        final List<NoiseRule> effectiveGlobal;

        ProjectRuleSet(long mtime, long size, Map<String, Boolean> overrides,
                       List<NoiseRule> customRules, List<NoiseRule> effectiveGlobal) {
            this.mtime = mtime;
            this.size = size;
            this.checkedAtMs = System.currentTimeMillis();
            this.overrides = overrides;
            this.customRules = customRules;
            this.effectiveGlobal = effectiveGlobal;
        }
    }

    public NoiseRuleService() {
        loadGlobal();
    }

    // ================================================================
    // 全局规则 API（向后兼容，不传 projectPath 的地方都用这个）
    // ================================================================

    /** 从文件加载全局规则；文件不存在则用默认规则并保存 */
    public synchronized void loadGlobal() {
        try {
            if (Files.exists(GLOBAL_RULES_FILE)) {
                List<NoiseRule> loaded = MAPPER.readValue(GLOBAL_RULES_FILE.toFile(),
                        new TypeReference<List<NoiseRule>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    globalRules = new ArrayList<>(loaded);
                    projectRuleCache.clear();
                    return;
                }
            }
        } catch (IOException e) {
            // 文件损坏时回退到默认规则
        }
        globalRules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
        projectRuleCache.clear();
    }

    /** 获取全局规则 */
    public List<NoiseRule> getRules() {
        return new ArrayList<>(globalRules);
    }

    /** 保存全局规则（全量覆盖） */
    public synchronized void save(List<NoiseRule> newRules) {
        globalRules = newRules != null ? new ArrayList<>(newRules) : new ArrayList<>();
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
        projectRuleCache.clear();
    }

    /** 恢复默认全局规则 */
    public synchronized void reset() {
        globalRules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
        projectRuleCache.clear();
    }

    // ================================================================
    // 项目级规则 API（带 projectPath 参数）
    // ================================================================

    /** 项目级规则文件路径：<项目>/.callgraph/noise-rules.json */
    public static Path projectRulesFile(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return GLOBAL_RULES_FILE;
        return Paths.get(projectPath, ".callgraph", "noise-rules.json");
    }

    /**
     * 取项目级规则集（带内存缓存）。
     * 命中窗口内直接返回内存值；否则比对文件 mtime/size，未变则只刷新复查时间，变了才重新读盘解析。
     */
    private ProjectRuleSet projectRuleSet(String projectPath) {
        String key = (projectPath == null || projectPath.isEmpty()) ? "" : projectPath;
        ProjectRuleSet cached = projectRuleCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.checkedAtMs < META_RECHECK_INTERVAL_MS) {
            return cached;
        }
        boolean hasProject = !key.isEmpty();
        Path file = hasProject ? projectRulesFile(key) : null;
        long mtime = -1L, size = -1L;
        if (hasProject) {
            try {
                if (Files.isRegularFile(file)) {
                    mtime = Files.getLastModifiedTime(file).toMillis();
                    size = Files.size(file);
                }
            } catch (IOException ignored) {
            }
        }
        if (cached != null && cached.mtime == mtime && cached.size == size) {
            cached.checkedAtMs = now;
            return cached;
        }
        ProjectRuleSet fresh = loadProjectRuleSet(file, mtime, size);
        projectRuleCache.put(key, fresh);
        return fresh;
    }

    /**
     * 解析项目规则文件（单次读盘）。兼容两种格式：
     *   - 旧版：JSON 数组 [NoiseRule, ...]  → 视为纯自定义规则，无覆盖配置
     *   - 新版：JSON 对象 {globalOverrides:{}, customRules:[...]}
     */
    private ProjectRuleSet loadProjectRuleSet(Path file, long mtime, long size) {
        Map<String, Boolean> overrides = new HashMap<>();
        List<NoiseRule> custom;
        if (file == null) {
            custom = new ArrayList<>();
        } else {
            JsonNode node = readNode(file);
            if (node != null && node.isArray()) {
                custom = convertRules(node);
            } else if (node != null && node.isObject() && node.has("customRules")) {
                if (node.has("globalOverrides")) {
                    node.get("globalOverrides").fields().forEachRemaining(e ->
                            overrides.put(e.getKey(), e.getValue().asBoolean(false)));
                }
                custom = convertRules(node.get("customRules"));
            } else {
                custom = new ArrayList<>();
            }
        }
        return new ProjectRuleSet(mtime, size, overrides, custom, applyOverrides(overrides));
    }

    /** 读取 JSON 文件；不存在或不可读返回 null */
    private JsonNode readNode(Path file) {
        try {
            if (Files.exists(file)) {
                return MAPPER.readTree(file.toFile());
            }
        } catch (IOException e) {
            log.warn("[噪声规则] 规则读取失败 {}: {}", file, e.getMessage());
        }
        return null;
    }

    /** JsonNode → List<NoiseRule>，解析失败返回空列表 */
    private static List<NoiseRule> convertRules(JsonNode node) {
        try {
            List<NoiseRule> loaded = MAPPER.convertValue(node, new TypeReference<List<NoiseRule>>() {});
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            log.warn("[噪声规则] 规则解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 项目级"全局规则覆盖配置"（ruleId → 是否启用），文件不存在/旧版格式返回空 Map */
    public Map<String, Boolean> getGlobalOverrides(String projectPath) {
        return new HashMap<>(projectRuleSet(projectPath).overrides);
    }

    /**
     * 项目级自定义规则（新版 customRules 字段；旧版数组格式时整个数组即自定义规则）。
     * 文件不存在返回空列表，不 fallback。
     */
    public List<NoiseRule> getProjectRules(String projectPath) {
        return new ArrayList<>(projectRuleSet(projectPath).customRules);
    }

    /**
     * 项目级配置详情（新版结构）：globalRules + globalOverrides + customRules。
     * 前端"项目级 Tab"用它同时渲染"全局规则覆盖区"和"自定义规则区"。
     */
    public Map<String, Object> getProjectDetail(String projectPath) {
        ProjectRuleSet rs = projectRuleSet(projectPath);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("globalRules", getRules());
        detail.put("globalOverrides", new LinkedHashMap<>(rs.overrides));
        detail.put("customRules", new ArrayList<>(rs.customRules));
        return detail;
    }

    /**
     * 保存项目级配置（新版结构：覆盖配置 + 自定义规则）。
     * 写入时自动把旧版数组格式升级为新版对象格式。
     */
    public synchronized void saveProjectDetail(String projectPath, Map<String, Boolean> globalOverrides,
                                              List<NoiseRule> customRules) {
        Path file = projectRulesFile(projectPath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("globalOverrides", globalOverrides != null ? globalOverrides : new HashMap<>());
        data.put("customRules", customRules != null ? new ArrayList<>(customRules) : new ArrayList<>());
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
            log.info("[噪声规则] 项目级配置已写入 {} (覆盖 {} 条, 自定义 {} 条)",
                    file, ((Map<?, ?>) data.get("globalOverrides")).size(),
                    ((List<?>) data.get("customRules")).size());
            projectRuleCache.remove(projectPath == null ? "" : projectPath);
        } catch (IOException e) {
            log.warn("[噪声规则] 项目级配置保存失败 {}: {}", file, e.getMessage());
        }
    }

    /** 保存项目级规则（全量覆盖，仅写自定义规则、保留既有覆盖配置） */
    public synchronized void saveProjectRules(String projectPath, List<NoiseRule> newRules) {
        saveProjectDetail(projectPath, getGlobalOverrides(projectPath), newRules);
    }

    /** 清空项目级规则（删文件） */
    public synchronized void resetProjectRules(String projectPath) {
        Path file = projectRulesFile(projectPath);
        try {
            Files.deleteIfExists(file);
            log.info("[噪声规则] 项目级规则已删除 {}", file);
            projectRuleCache.remove(projectPath == null ? "" : projectPath);
        } catch (IOException e) {
            log.warn("[噪声规则] 项目级规则删除失败 {}: {}", file, e.getMessage());
        }
    }

    /** 将全局规则套用项目覆盖配置，得到"该项目视角下实际生效的全局规则" */
    private List<NoiseRule> applyOverrides(Map<String, Boolean> overrides) {
        if (overrides == null || overrides.isEmpty()) return globalRules;
        List<NoiseRule> effective = new ArrayList<>();
        for (NoiseRule r : globalRules) {
            Boolean ov = r.getId() != null ? overrides.get(r.getId()) : null;
            if (ov != null) {
                effective.add(new NoiseRule(r.getId(), r.getName(), r.getMethodPattern(),
                        r.getClassPattern(), r.getSource(), r.getParamCount(), ov));
            } else {
                effective.add(r);
            }
        }
        return effective;
    }

    // ================================================================
    // 判断入口（合并两层）
    // ================================================================

    /**
     * 判断方法是否为噪声——**仅查全局规则**（向后兼容）。
     * 分析主流程请用 {@link #isNoise(String, String, String)} 传 projectPath。
     */
    public boolean isNoise(String methodIdentifier, String source) {
        return isNoiseOnRules(globalRules, methodIdentifier, source);
    }

    /**
     * 判断方法是否为噪声——**全局 + 项目级合并**。
     * 任一命中即算噪声。项目级配置可覆盖全局规则的启用/禁用状态。
     *
     * @param projectPath 项目路径（可为 null，null 时只查全局）
     */
    public boolean isNoise(String methodIdentifier, String source, String projectPath) {
        ProjectRuleSet rs = projectRuleSet(projectPath);
        if (isNoiseOnRules(rs.effectiveGlobal, methodIdentifier, source)) return true;
        return isNoiseOnRules(rs.customRules, methodIdentifier, source);
    }

    /** 仅全局 getMatchedRule（向后兼容） */
    public String getMatchedRule(String methodIdentifier, String source) {
        return getMatchedRuleOnRules(globalRules, methodIdentifier, source);
    }

    /** 合并两层 getMatchedRule，命中规则名用逗号分隔（标注来源 G/P） */
    public String getMatchedRule(String methodIdentifier, String source, String projectPath) {
        ProjectRuleSet rs = projectRuleSet(projectPath);
        List<String> names = new ArrayList<>();
        String g = getMatchedRuleOnRules(rs.effectiveGlobal, methodIdentifier, source);
        if (g != null) names.add("[G] " + g);
        String p = getMatchedRuleOnRules(rs.customRules, methodIdentifier, source);
        if (p != null) names.add("[P] " + p);
        return names.isEmpty() ? null : String.join(", ", names);
    }

    // ================================================================
    // 内部匹配逻辑（同一套算法，对任意规则列表）
    // ================================================================

    /** 对指定规则列表做噪声判断 */
    private boolean isNoiseOnRules(List<NoiseRule> rules, String methodIdentifier, String source) {
        if (rules == null || rules.isEmpty() || methodIdentifier == null) return false;
        String[] parsed = parseMethodSignature(methodIdentifier);
        String className = parsed[0];
        String methodName = parsed[1];
        int paramCount = Integer.parseInt(parsed[2]);

        for (NoiseRule r : rules) {
            if (!r.isEnabled()) continue;
            if (r.getSource() != null && !r.getSource().isEmpty()
                    && !"ALL".equalsIgnoreCase(r.getSource())) {
                if (!r.getSource().equalsIgnoreCase(source)) continue;
            }
            if (!matches(r.getMethodPattern(), methodName)) continue;
            if (r.getClassPattern() != null && !r.getClassPattern().isEmpty()) {
                if (className == null || !matches(r.getClassPattern(), className)) continue;
            }
            if (r.getParamCount() != null && r.getParamCount() != paramCount) continue;
            return true;
        }
        return false;
    }

    /** 对指定规则列表做命中规则名查找 */
    private String getMatchedRuleOnRules(List<NoiseRule> rules, String methodIdentifier, String source) {
        if (rules == null || rules.isEmpty() || methodIdentifier == null) return null;
        List<String> names = new ArrayList<>();
        String[] parsed = parseMethodSignature(methodIdentifier);
        String className = parsed[0];
        String methodName = parsed[1];
        int paramCount = Integer.parseInt(parsed[2]);

        for (NoiseRule r : rules) {
            if (!r.isEnabled()) continue;
            if (r.getSource() != null && !r.getSource().isEmpty()
                    && !"ALL".equalsIgnoreCase(r.getSource())) {
                if (!r.getSource().equalsIgnoreCase(source)) continue;
            }
            if (!matches(r.getMethodPattern(), methodName)) continue;
            if (r.getClassPattern() != null && !r.getClassPattern().isEmpty()) {
                if (className == null || !matches(r.getClassPattern(), className)) continue;
            }
            if (r.getParamCount() != null && r.getParamCount() != paramCount) continue;
            names.add(r.getName());
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /**
     * 解析方法签名 → [className, methodName, paramCount]
     * 例如 "com.foo.Bar#pay(String, int)" → ["com.foo.Bar", "pay", "2"]
     */
    private static String[] parseMethodSignature(String methodIdentifier) {
        int hash = methodIdentifier.indexOf('#');
        String className = hash >= 0 ? methodIdentifier.substring(0, hash) : "";
        String methodWithArgs = hash >= 0 ? methodIdentifier.substring(hash + 1) : methodIdentifier;
        int paren = methodWithArgs.indexOf('(');
        String methodName = paren >= 0 ? methodWithArgs.substring(0, paren) : methodWithArgs;
        int paramCount = parseParamCount(methodWithArgs);
        return new String[]{className, methodName, String.valueOf(paramCount)};
    }

    /**
     * 从方法签名中解析参数个数。
     * 例如 "pay(String, int)" -> 2，"getName()" -> 0。
     * 能正确处理泛型中的逗号，如 "query(Map<String, Integer>)" -> 1。
     */
    static int parseParamCount(String methodWithArgs) {
        if (methodWithArgs == null) return 0;
        int paren = methodWithArgs.indexOf('(');
        if (paren < 0) return 0;
        int closeParen = methodWithArgs.indexOf(')', paren);
        if (closeParen < 0) closeParen = methodWithArgs.length();
        String params = methodWithArgs.substring(paren + 1, closeParen).trim();
        if (params.isEmpty()) return 0;
        int depth = 0;
        int count = 1;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<' || c == '(') depth++;
            else if (c == '>' || c == ')') depth--;
            else if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    private static boolean matches(String regex, String input) {
        if (regex == null || regex.isEmpty()) return true;
        if (input == null) return false;
        try {
            return Pattern.compile(regex).matcher(input).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static void saveQuietly(Path file, List<NoiseRule> rules) {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), rules);
        } catch (IOException ignored) {
            // 保存失败不影响运行（内存规则仍生效）
        }
    }
}
