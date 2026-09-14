package com.spark.callgraph.service;

import com.spark.callgraph.config.CallgraphPaths;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
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
                    return;
                }
            }
        } catch (IOException e) {
            // 文件损坏时回退到默认规则
        }
        globalRules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
    }

    /** 获取全局规则 */
    public List<NoiseRule> getRules() {
        return new ArrayList<>(globalRules);
    }

    /** 保存全局规则（全量覆盖） */
    public synchronized void save(List<NoiseRule> newRules) {
        globalRules = newRules != null ? new ArrayList<>(newRules) : new ArrayList<>();
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
    }

    /** 恢复默认全局规则 */
    public synchronized void reset() {
        globalRules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly(GLOBAL_RULES_FILE, globalRules);
    }

    // ================================================================
    // 项目级规则 API（带 projectPath 参数）
    // ================================================================

    /** 项目级规则文件路径：<项目>/.callgraph/noise-rules.json */
    public static Path projectRulesFile(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return GLOBAL_RULES_FILE;
        return Paths.get(projectPath, ".callgraph", "noise-rules.json");
    }

    /** 获取项目级规则（文件不存在返回空列表，不 fallback） */
    public List<NoiseRule> getProjectRules(String projectPath) {
        Path file = projectRulesFile(projectPath);
        try {
            if (Files.exists(file)) {
                List<NoiseRule> loaded = MAPPER.readValue(file.toFile(),
                        new TypeReference<List<NoiseRule>>() {});
                if (loaded != null) return loaded;
            }
        } catch (IOException e) {
            log.warn("[噪声规则] 项目级规则读取失败 {}: {}", file, e.getMessage());
        }
        return new ArrayList<>();
    }

    /** 保存项目级规则（全量覆盖） */
    public synchronized void saveProjectRules(String projectPath, List<NoiseRule> newRules) {
        Path file = projectRulesFile(projectPath);
        List<NoiseRule> toSave = newRules != null ? new ArrayList<>(newRules) : new ArrayList<>();
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), toSave);
            log.info("[噪声规则] 项目级规则已写入 {} ({} 条)", file, toSave.size());
        } catch (IOException e) {
            log.warn("[噪声规则] 项目级规则保存失败 {}: {}", file, e.getMessage());
        }
    }

    /** 清空项目级规则（删文件） */
    public synchronized void resetProjectRules(String projectPath) {
        Path file = projectRulesFile(projectPath);
        try {
            Files.deleteIfExists(file);
            log.info("[噪声规则] 项目级规则已删除 {}", file);
        } catch (IOException e) {
            log.warn("[噪声规则] 项目级规则删除失败 {}: {}", file, e.getMessage());
        }
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
     * 任一命中即算噪声。
     *
     * @param projectPath 项目路径（可为 null，null 时只查全局）
     */
    public boolean isNoise(String methodIdentifier, String source, String projectPath) {
        if (isNoiseOnRules(globalRules, methodIdentifier, source)) return true;
        List<NoiseRule> project = getProjectRules(projectPath);
        return isNoiseOnRules(project, methodIdentifier, source);
    }

    /** 仅全局 getMatchedRule（向后兼容） */
    public String getMatchedRule(String methodIdentifier, String source) {
        return getMatchedRuleOnRules(globalRules, methodIdentifier, source);
    }

    /** 合并两层 getMatchedRule，命中规则名用逗号分隔（标注来源 G/P） */
    public String getMatchedRule(String methodIdentifier, String source, String projectPath) {
        List<String> names = new ArrayList<>();
        String g = getMatchedRuleOnRules(globalRules, methodIdentifier, source);
        if (g != null) names.add("[G] " + g);
        List<NoiseRule> project = getProjectRules(projectPath);
        String p = getMatchedRuleOnRules(project, methodIdentifier, source);
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
