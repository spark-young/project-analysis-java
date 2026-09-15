package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描规则：用户自定义的入口匹配规则，由通用规则引擎 RuleBasedScanner 执行。
 * <p>
 * 6 种 kind：
 *   ANNOTATION_METHOD    扫描标注了指定注解的方法
 *   ANNOTATION_CLASS     扫描标注了指定注解的类（取其所有 public 方法）
 *   PACKAGE              扫描指定包（可含子包）下所有类
 *   INTERFACE_IMPLEMENT  扫描实现/继承了某接口/父类的类
 *   CLASS_NAME           类名通配模式匹配
 *   METHOD_NAME          方法名通配模式匹配
 */
public class ScanRule {

    public static final String KIND_ANNOTATION_METHOD = "ANNOTATION_METHOD";
    public static final String KIND_ANNOTATION_CLASS = "ANNOTATION_CLASS";
    public static final String KIND_PACKAGE = "PACKAGE";
    public static final String KIND_INTERFACE_IMPLEMENT = "INTERFACE_IMPLEMENT";
    public static final String KIND_CLASS_NAME = "CLASS_NAME";
    public static final String KIND_METHOD_NAME = "METHOD_NAME";

    private String id;
    private String name;
    private boolean enabled = true;
    private String kind;

    /** kind 1-2：注解全限定名（点分隔，如 com.foo.MyAnnotation） */
    private String annotation;
    /** kind 3：包名（点分隔，如 com.foo.trade） */
    private String packagePrefix;
    /** kind 3：是否递归子包 */
    private boolean recursive = true;
    /** kind 4：接口/基类全限定名（点分隔） */
    private String interfaceName;
    /** kind 5-6：通配模式，支持 * 与 **（如 *Controller、handle*） */
    private String pattern;
    /** 排除的类模式列表（支持通配符） */
    private List<String> excludes = new ArrayList<>();

    public ScanRule() {}

    // ------------------------------------------------------------------
    // 便捷工厂（用于内置预设/测试）
    // ------------------------------------------------------------------

    public static ScanRule annotationMethod(String id, String name, String annotation) {
        ScanRule r = new ScanRule();
        r.id = id;
        r.name = name;
        r.kind = KIND_ANNOTATION_METHOD;
        r.annotation = annotation;
        return r;
    }

    public static ScanRule packageRule(String id, String name, String packagePrefix, boolean recursive) {
        ScanRule r = new ScanRule();
        r.id = id;
        r.name = name;
        r.kind = KIND_PACKAGE;
        r.packagePrefix = packagePrefix;
        r.recursive = recursive;
        return r;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }
    public String getPackagePrefix() { return packagePrefix; }
    public void setPackagePrefix(String packagePrefix) { this.packagePrefix = packagePrefix; }
    public boolean isRecursive() { return recursive; }
    public void setRecursive(boolean recursive) { this.recursive = recursive; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public List<String> getExcludes() { return excludes; }
    public void setExcludes(List<String> excludes) { this.excludes = excludes; }
}
