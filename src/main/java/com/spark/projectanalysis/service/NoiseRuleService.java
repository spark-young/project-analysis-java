package com.spark.projectanalysis.service;

import com.spark.projectanalysis.config.CallgraphPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.projectanalysis.service.dto.NoiseProjectDetail;
import com.spark.projectanalysis.service.dto.NoiseRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 正则编译结果缓存：key = 正则本体（本匹配路径的 flags 恒为默认 0）。
     * isNoise/getMatchedRule 是导出与页面的热路径，同一规则会反复匹配成千上万个方法，
     * 缓存后避免每次调用都 Pattern.compile。
     * 非法正则的 PatternSyntaxException 由 computeIfAbsent 抛出且不入缓存，行为与改前一致。
     */
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /** 全局规则文件路径 */
    private static final Path GLOBAL_RULES_FILE = CallgraphPaths.noiseRulesFile();

    /** 默认（内置）规则集：随 jar 打包，本体以代码定义为准，只有 enabled 允许用户改动 */
    private static final List<NoiseRule> DEFAULT_RULES = Arrays.asList(
            builtin("factory", "单例/工厂",
                    "getInstance|getBean|getBeanFactory|getInstance.*", "", "ALL", true),
            builtin("constructor", "构造器",
                    "<init>", "", "ALL", true),
            builtin("object-method", "Object 方法",
                    "toString|equals|hashCode|getClass|clone|finalize", "", "ALL", true),
            builtin("logging", "日志",
                    "(info|debug|error|warn|trace|fatal)", ".*(Logger|Log|Slf4j).*", "ALL", true),
            builtin("lifecycle", "Spring 生命周期",
                    "afterPropertiesSet|initMethod|destroy|initialize|dispose|onApplicationEvent|postProcess.*",
                    "", "ALL", true),
            // getter：仅匹配 0 个参数的 getXxx/isXxx，避免误杀 getUserById 等带参业务方法
            builtin("simple-getter", "简单 getter（0 参数，getXxx/isXxx）",
                    "^(get|is)[A-Z]", "", "PROJECT", 0, true),
            // setter：仅匹配 1 个参数的 setXxx
            builtin("simple-setter", "简单 setter（1 参数，setXxx）",
                    "^set[A-Z]", "", "PROJECT", 1, true)
    );

    private static NoiseRule builtin(String id, String name, String methodPattern,
                                     String classPattern, String source, boolean enabled) {
        return builtin(id, name, methodPattern, classPattern, source, null, enabled);
    }

    private static NoiseRule builtin(String id, String name, String methodPattern,
                                    String classPattern, String source, Integer paramCount, boolean enabled) {
        NoiseRule r = new NoiseRule(id, name, methodPattern, classPattern, source, paramCount, enabled);
        r.setBuiltin(true);
        return r;
    }

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

    /** 从文件加载全局规则并与内置规则合并；文件不存在/损坏则用内置默认 */
    public synchronized void loadGlobal() {
        Path file = GLOBAL_RULES_FILE;
        List<NoiseRule> loaded = null;
        if (Files.exists(file)) {
            try {
                loaded = MAPPER.readValue(file.toFile(), new TypeReference<List<NoiseRule>>() {});
            } catch (IOException e) {
                log.warn("[噪声规则] 全局规则文件损坏，重建默认: {}", e.getMessage());
            }
        }
        globalRules = mergeWithBuiltins(loaded);
        // 归一化后回写：文件里始终是「当前版本的内置定义 + 用户启用状态 + 自定义规则」，
        // 这样升级 jar 后内置规则会自动刷新，用户改动也只体现在 enabled 上
        saveQuietly(file, globalRules);
        projectRuleCache.clear();
    }

    /** 获取全局规则 */
    public List<NoiseRule> getRules() {
        return new ArrayList<>(globalRules);
    }

    /** 保存全局规则（全量覆盖）；内置规则的**本体仍以代码为准**，只有启用状态会被采纳 */
    public synchronized void save(List<NoiseRule> newRules) {
        globalRules = mergeWithBuiltins(newRules);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
        projectRuleCache.clear();
    }

    /** 恢复默认全局规则（回到内置定义） */
    public synchronized void reset() {
        globalRules = mergeWithBuiltins(null);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
        projectRuleCache.clear();
    }

    // ================================================================
    // 内置规则归一化（本体以代码为准）
    // ================================================================

    /**
     * 把外部（磁盘文件 / 前端传回 / 导入文件）的规则列表与代码内置规则合并，产出新的全局规则集。
     *
     * 规则：
     *   1) 代码内置规则始终存在，且**本体以代码为准**（名称/正则/来源/参数数不接受外部值）；
     *      只有 enabled 采用外部给的值，缺省用代码默认。
     *   2) 外部标记 builtin、但 id 已不在代码里的条目（历史版本下线的内置规则）直接丢弃。
     *   3) 未标记 builtin、但内容与某条内置规则完全一致的条目（老版本保存时丢了 id 的内置副本）
     *      被吸收为该内置规则的启用状态，不再作为自定义规则保留 —— 避免升级后出现重复规则。
     *   4) 其余条目作为用户自定义规则原样保留。
     */
    private static List<NoiseRule> mergeWithBuiltins(List<NoiseRule> fromStore) {
        Map<String, Boolean> enabledByBuiltinId = new HashMap<>();
        List<NoiseRule> customs = new ArrayList<>();
        if (fromStore != null) {
            for (NoiseRule r : fromStore) {
                if (r == null || r.getId() == null || r.getId().isEmpty()) continue;
                if (isBuiltinId(r.getId())) {
                    enabledByBuiltinId.put(r.getId(), r.isEnabled());
                    continue;
                }
                if (r.isBuiltin()) continue;
                String matched = matchBuiltinId(r);
                if (matched != null) {
                    enabledByBuiltinId.putIfAbsent(matched, r.isEnabled());
                    continue;
                }
                customs.add(r);
            }
        }
        List<NoiseRule> out = new ArrayList<>(DEFAULT_RULES.size() + customs.size());
        for (NoiseRule b : DEFAULT_RULES) {
            NoiseRule copy = new NoiseRule(b.getId(), b.getName(), b.getMethodPattern(),
                    b.getClassPattern(), b.getSource(), b.getParamCount(), b.isEnabled());
            copy.setBuiltin(true);
            Boolean ov = enabledByBuiltinId.get(b.getId());
            if (ov != null) copy.setEnabled(ov);
            out.add(copy);
        }
        out.addAll(customs);
        return out;
    }

    private static boolean isBuiltinId(String id) {
        if (id == null) return false;
        for (NoiseRule b : DEFAULT_RULES) {
            if (id.equals(b.getId())) return true;
        }
        return false;
    }

    /** 按「名称 + 方法名正则 + 类名正则 + 来源 + 参数数」全等匹配内置规则，返回其 id */
    private static String matchBuiltinId(NoiseRule r) {
        for (NoiseRule b : DEFAULT_RULES) {
            if (eq(b.getName(), r.getName())
                    && eq(b.getMethodPattern(), r.getMethodPattern())
                    && eq(b.getClassPattern(), r.getClassPattern())
                    && eq(b.getSource(), r.getSource())
                    && (b.getParamCount() == null
                        ? r.getParamCount() == null
                        : b.getParamCount().equals(r.getParamCount()))) {
                return b.getId();
            }
        }
        return null;
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    // ================================================================
    // 项目级规则 API（带 projectPath 参数）
    // ================================================================

    /** 项目级规则文件路径：<项目>/.callgraph/noise-rules.json */
    public static Path projectRulesFile(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return GLOBAL_RULES_FILE;
        return CallgraphPaths.projectDataDir(projectPath).resolve("noise-rules.json");
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
    public NoiseProjectDetail getProjectDetail(String projectPath) {
        ProjectRuleSet rs = projectRuleSet(projectPath);
        NoiseProjectDetail detail = new NoiseProjectDetail();
        detail.setGlobalRules(getRules());
        detail.setGlobalOverrides(new LinkedHashMap<>(rs.overrides));
        detail.setCustomRules(new ArrayList<>(rs.customRules));
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

    /** 保存项目级规则（全量覆盖，仅写自定义规则、保留既有覆盖配置）；内置规则不入项目层 */
    public synchronized void saveProjectRules(String projectPath, List<NoiseRule> newRules) {
        List<NoiseRule> customs = new ArrayList<>();
        if (newRules != null) {
            for (NoiseRule r : newRules) {
                if (r != null && !r.isBuiltin()) customs.add(r);
            }
        }
        saveProjectDetail(projectPath, getGlobalOverrides(projectPath), customs);
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
                NoiseRule c = new NoiseRule(r.getId(), r.getName(), r.getMethodPattern(),
                        r.getClassPattern(), r.getSource(), r.getParamCount(), ov);
                c.setBuiltin(r.isBuiltin());
                effective.add(c);
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
            return compiledPattern(regex).matcher(input).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /** 取缓存的编译结果；未命中则由 Pattern.compile 编译后放入（非法正则抛出且不缓存） */
    private static Pattern compiledPattern(String regex) {
        return PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
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
