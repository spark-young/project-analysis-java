package com.spark.callgraph.service;

import com.spark.callgraph.config.CallgraphPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.callgraph.service.dto.NoiseRule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 样板方法过滤规则管理：
 * - 内置默认规则集
 * - 持久化到 ~/.callgraph/noise-rules.json
 * - 支持增删改、恢复默认
 * - 判断某个方法是否为噪声
 */
@Service
public class NoiseRuleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 规则文件路径 */
    private static final Path RULES_FILE = CallgraphPaths.noiseRulesFile();

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

    /** 内存中的规则（从文件加载或默认） */
    private volatile List<NoiseRule> rules = new ArrayList<>(DEFAULT_RULES);

    public NoiseRuleService() {
        load();
    }

    /** 从文件加载规则；文件不存在则用默认规则并保存 */
    public synchronized void load() {
        try {
            if (Files.exists(RULES_FILE)) {
                List<NoiseRule> loaded = MAPPER.readValue(RULES_FILE.toFile(),
                        new TypeReference<List<NoiseRule>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    rules = new ArrayList<>(loaded);
                    return;
                }
            }
        } catch (IOException e) {
            // 文件损坏时回退到默认规则
        }
        rules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly();
    }

    /** 获取当前规则 */
    public List<NoiseRule> getRules() {
        return new ArrayList<>(rules);
    }

    /** 保存规则（全量覆盖） */
    public synchronized void save(List<NoiseRule> newRules) {
        rules = newRules != null ? new ArrayList<>(newRules) : new ArrayList<>();
        saveQuietly();
    }

    /** 恢复默认规则 */
    public synchronized void reset() {
        rules = new ArrayList<>(DEFAULT_RULES);
        saveQuietly();
    }

    /**
     * 判断方法是否为噪声（命中任一启用规则）。
     *
     * @param methodIdentifier 方法全限定标识，如 com.foo.Bar#pay(String)
     * @param source           来源：PROJECT / DEPENDENCY / EXTERNAL
     */
    public boolean isNoise(String methodIdentifier, String source) {
        if (methodIdentifier == null) return false;
        // 解析类名、方法名、参数个数
        int hash = methodIdentifier.indexOf('#');
        String className = hash >= 0 ? methodIdentifier.substring(0, hash) : "";
        String methodWithArgs = hash >= 0 ? methodIdentifier.substring(hash + 1) : methodIdentifier;
        int paren = methodWithArgs.indexOf('(');
        String methodName = paren >= 0 ? methodWithArgs.substring(0, paren) : methodWithArgs;
        int paramCount = parseParamCount(methodWithArgs);

        for (NoiseRule r : rules) {
            if (!r.isEnabled()) continue;
            // 来源匹配
            if (r.getSource() != null && !r.getSource().isEmpty()
                    && !"ALL".equalsIgnoreCase(r.getSource())) {
                if (!r.getSource().equalsIgnoreCase(source)) continue;
            }
            // 方法名匹配
            if (!matches(r.getMethodPattern(), methodName)) continue;
            // 类名匹配（规则未配置类正则则跳过）
            if (r.getClassPattern() != null && !r.getClassPattern().isEmpty()) {
                if (className == null || !matches(r.getClassPattern(), className)) continue;
            }
            // 参数个数匹配（规则未配置则跳过）
            if (r.getParamCount() != null && r.getParamCount() != paramCount) continue;
            return true;
        }
        return false;
    }

    /**
     * 返回方法命中的规则名称（多条用逗号分隔），未命中返回 null。
     */
    public String getMatchedRule(String methodIdentifier, String source) {
        if (methodIdentifier == null) return null;
        List<String> names = new ArrayList<>();
        int hash = methodIdentifier.indexOf('#');
        String className = hash >= 0 ? methodIdentifier.substring(0, hash) : "";
        String methodWithArgs = hash >= 0 ? methodIdentifier.substring(hash + 1) : methodIdentifier;
        int paren = methodWithArgs.indexOf('(');
        String methodName = paren >= 0 ? methodWithArgs.substring(0, paren) : methodWithArgs;
        int paramCount = parseParamCount(methodWithArgs);

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
     * 从方法签名中解析参数个数。
     * 例如 "pay(String, int)" -> 2，"getName()" -> 0，"pay()" -> 0。
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
        // 空正则 = 匹配所有（与前端一致）
        if (regex == null || regex.isEmpty()) return true;
        if (input == null) return false;
        try {
            return Pattern.compile(regex).matcher(input).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private void saveQuietly() {
        try {
            Files.createDirectories(RULES_FILE.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(RULES_FILE.toFile(), rules);
        } catch (IOException ignored) {
            // 保存失败不影响运行（内存规则仍生效）
        }
    }
}
