package com.spark.callgraph.engine.entry;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.service.dto.ScanRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用规则扫描引擎：按用户配置的 {@link ScanRule} 从注册表识别入口。
 * <p>
 * 与内置探测器（REST/DUBBO/...）并行工作，两者结果合并去重。
 * 规则产出的入口 group 固定为 {@code RULE}，display 标注命中的规则名。
 */
public final class RuleBasedScanner {

    /** 规则入口的分组标签 */
    public static final String GROUP = "RULE";

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;

    private RuleBasedScanner() {}

    /**
     * 执行规则扫描。
     *
     * @param registry ASM 注册表
     * @param rules    规则列表（内部会过滤 enabled）
     * @return 命中的入口列表（已按 className#method#descriptor 去重）
     */
    public static List<EntryPoint> scan(ClassMetadataRegistry registry, List<ScanRule> rules) {
        List<EntryPoint> out = new ArrayList<>();
        if (registry == null || rules == null || rules.isEmpty()) return out;

        List<ScanRule> enabled = new ArrayList<>();
        for (ScanRule r : rules) {
            if (r != null && r.isEnabled() && r.getKind() != null) enabled.add(r);
        }
        if (enabled.isEmpty()) return out;

        Set<String> seen = new LinkedHashSet<>();

        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != SourceType.PROJECT) continue;
            if (ci.isInterface() || ci.isAbstract()) continue;

            String fqn = ci.getInternalName().replace('/', '.');
            if (isExcluded(fqn, enabled)) continue;

            for (ScanRule rule : enabled) {
                List<MethodKey> matched = matchClass(rule, ci, fqn);
                if (matched == null || matched.isEmpty()) continue;
                for (MethodKey m : matched) {
                    if (!isPublicConcrete(ci, m)) continue;
                    String key = fqn + "#" + m.getName() + "#" + m.getDescriptor();
                    if (!seen.add(key)) continue;
                    String display = "[" + (rule.getName() == null ? rule.getKind() : rule.getName()) + "] "
                            + m.getName();
                    out.add(new EntryPoint(GROUP, fqn, m.getName(), m.getDescriptor(), display));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 规则匹配
    // ------------------------------------------------------------------

    /**
     * 判断类是否命中规则，命中则返回应纳入入口的方法集合。
     * 返回 null / 空表示未命中。
     */
    private static List<MethodKey> matchClass(ScanRule rule, ClassInfo ci, String fqn) {
        String kind = rule.getKind();
        switch (kind == null ? "" : kind) {
            case ScanRule.KIND_ANNOTATION_METHOD:
                return matchAnnotationMethod(rule, ci);
            case ScanRule.KIND_ANNOTATION_CLASS:
                if (hasClassAnnotation(ci, rule.getAnnotation())) return publicMethods(ci);
                return null;
            case ScanRule.KIND_PACKAGE:
                if (matchPackage(fqn, rule)) return publicMethods(ci);
                return null;
            case ScanRule.KIND_INTERFACE_IMPLEMENT:
                if (matchInterface(ci, rule.getInterfaceName())) return publicMethods(ci);
                return null;
            case ScanRule.KIND_CLASS_NAME:
                if (matchClassName(fqn, rule.getPattern())) return publicMethods(ci);
                return null;
            case ScanRule.KIND_METHOD_NAME:
                return matchMethodName(rule, ci);
            default:
                return null;
        }
    }

    /** 注解标在方法上 → 该注解方法本身 */
    private static List<MethodKey> matchAnnotationMethod(ScanRule rule, ClassInfo ci) {
        String annInternal = toInternalName(rule.getAnnotation());
        if (annInternal == null || annInternal.isEmpty()) return null;
        List<MethodKey> out = new ArrayList<>();
        for (MethodKey m : ci.methodKeys()) {
            if (isConstructor(m)) continue;
            if (ci.methodAnnotation(m.getName(), m.getDescriptor(), annInternal) != null) {
                out.add(m);
            }
        }
        return out;
    }

    /** 方法名通配模式 → 匹配的方法 */
    private static List<MethodKey> matchMethodName(ScanRule rule, ClassInfo ci) {
        String regex = wildcardToRegex(rule.getPattern());
        if (regex == null) return null;
        Pattern p = compile(regex);
        if (p == null) return null;
        List<MethodKey> out = new ArrayList<>();
        for (MethodKey m : ci.methodKeys()) {
            if (isConstructor(m)) continue;
            if (p.matcher(m.getName()).matches()) out.add(m);
        }
        return out;
    }

    /** 类的所有 public 非构造方法 */
    private static List<MethodKey> publicMethods(ClassInfo ci) {
        List<MethodKey> out = new ArrayList<>();
        for (MethodKey m : ci.methodKeys()) {
            if (isConstructor(m)) continue;
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 判定辅助
    // ------------------------------------------------------------------

    private static boolean hasClassAnnotation(ClassInfo ci, String annotation) {
        String internal = toInternalName(annotation);
        return internal != null && !internal.isEmpty() && ci.annotation(internal) != null;
    }

    private static boolean matchPackage(String fqn, ScanRule rule) {
        String pkg = rule.getPackagePrefix();
        if (pkg == null || pkg.isEmpty()) return false;
        pkg = pkg.trim();
        if (rule.isRecursive()) {
            return fqn.startsWith(pkg + ".");
        }
        // 非递归：类的包名精确等于 pkg
        int idx = fqn.lastIndexOf('.');
        String classPkg = idx < 0 ? "" : fqn.substring(0, idx);
        return classPkg.equals(pkg);
    }

    private static boolean matchInterface(ClassInfo ci, String interfaceName) {
        if (interfaceName == null || interfaceName.isEmpty()) return false;
        String target = toInternalName(interfaceName);
        if (target == null || target.isEmpty()) return false;
        if (ci.getInternalName().equals(target)) return true;
        if (target.equals(ci.getSuperName())) return true;
        return ci.getInterfaces().contains(target);
    }

    private static boolean matchClassName(String fqn, String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;
        String regex = wildcardToRegex(pattern);
        Pattern p = compile(regex);
        if (p == null) return false;
        // 含点 → 匹配全限定名；否则匹配简单类名
        String target = pattern.contains(".")
                ? fqn
                : fqn.substring(fqn.lastIndexOf('.') + 1);
        return p.matcher(target).matches();
    }

    private static boolean isExcluded(String fqn, List<ScanRule> rules) {
        // 任一规则配置了命中当前类，就跳过该类（排除优先级最高）
        for (ScanRule r : rules) {
            List<String> excludes = r.getExcludes();
            if (excludes == null || excludes.isEmpty()) continue;
            for (String ex : excludes) {
                if (ex == null || ex.isEmpty()) continue;
                String regex = wildcardToRegex(ex);
                Pattern p = compile(regex);
                if (p != null && p.matcher(fqn).matches()) return true;
            }
        }
        return false;
    }

    private static boolean isPublicConcrete(ClassInfo ci, MethodKey m) {
        int access = ci.methodAccess(m.getName(), m.getDescriptor());
        if ((access & ACC_PUBLIC) == 0) return false;
        if ((access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0) return false;
        return ci.isMethodConcrete(m.getName(), m.getDescriptor());
    }

    private static boolean isConstructor(MethodKey m) {
        return m.getName().startsWith("<");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 注解/接口全限定名（点分隔）→ ASM 内部名（斜杠分隔） */
    private static String toInternalName(String fqn) {
        if (fqn == null) return null;
        String s = fqn.trim();
        if (s.isEmpty()) return null;
        return s.replace('.', '/');
    }

    /**
     * 通配符转正则：
     *   ** → 任意字符（含 .）
     *   *  → 任意字符（不含 .）
     *   其余字符原样转义
     */
    static String wildcardToRegex(String wildcard) {
        if (wildcard == null) return null;
        String w = wildcard.trim();
        if (w.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < w.length()) {
            char c = w.charAt(i);
            if (c == '*') {
                if (i + 1 < w.length() && w.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                    continue;
                }
                sb.append("[^.]*");
                i++;
                continue;
            }
            if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static Pattern compile(String regex) {
        if (regex == null || regex.isEmpty()) return null;
        try {
            return Pattern.compile(regex);
        } catch (Exception e) {
            return null;
        }
    }

    /** 供 UI 生成规则摘要 */
    public static String describe(ScanRule rule) {
        if (rule == null) return "";
        Map<String, String> parts = new LinkedHashMap<>();
        if (rule.getAnnotation() != null) parts.put("注解", rule.getAnnotation());
        if (rule.getPackagePrefix() != null) parts.put("包", rule.getPackagePrefix());
        if (rule.getInterfaceName() != null) parts.put("接口", rule.getInterfaceName());
        if (rule.getPattern() != null) parts.put("模式", rule.getPattern());
        return parts.toString();
    }
}
