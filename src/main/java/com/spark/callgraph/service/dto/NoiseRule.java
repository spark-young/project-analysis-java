package com.spark.callgraph.service.dto;

/**
 * 样板方法过滤规则。
 * 用于"方法调用次数分析"中剔除用户不关注的方法（如 getInstance、构造器、日志等）。
 * 匹配逻辑：方法名正则匹配 且（类名正则为空 或 类名匹配）且 来源匹配 且（参数个数未设置 或 参数个数匹配）。
 */
public class NoiseRule {

    private String id;
    private String name;            // 规则名称，如"单例/工厂"
    private String methodPattern;   // 方法名正则（必填）
    private String classPattern;    // 类名正则（可选，为空则不限制类）
    private String source;          // 来源：ALL / PROJECT / DEPENDENCY / EXTERNAL
    private Integer paramCount;     // 参数个数限制（null 表示不限制；设置后方法必须恰好有这么多参数才命中）
    private boolean enabled;        // 是否启用
    /**
     * 是否为工具内置规则（随 jar 打包的默认规则）。
     * 内置规则的**本体**（名称/正则/来源/参数数）以代码定义为准，磁盘与前端传回的值都会被忽略；
     * 只有 enabled 允许用户改动。
     */
    private boolean builtin;

    public NoiseRule() {
    }

    public NoiseRule(String id, String name, String methodPattern, String classPattern,
                     String source, boolean enabled) {
        this(id, name, methodPattern, classPattern, source, null, enabled);
    }

    public NoiseRule(String id, String name, String methodPattern, String classPattern,
                     String source, Integer paramCount, boolean enabled) {
        this.id = id;
        this.name = name;
        this.methodPattern = methodPattern;
        this.classPattern = classPattern;
        this.source = source;
        this.paramCount = paramCount;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMethodPattern() { return methodPattern; }
    public void setMethodPattern(String methodPattern) { this.methodPattern = methodPattern; }

    public String getClassPattern() { return classPattern; }
    public void setClassPattern(String classPattern) { this.classPattern = classPattern; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getParamCount() { return paramCount; }
    public void setParamCount(Integer paramCount) { this.paramCount = paramCount; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }
}
