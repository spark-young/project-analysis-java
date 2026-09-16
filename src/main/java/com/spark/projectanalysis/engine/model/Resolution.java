package com.spark.projectanalysis.engine.model;

import java.util.Collections;
import java.util.List;

/**
 * 调用点解析结果：单个具体方法 / 多个接口实现 / JDK（排除）/ 外部未知类。
 */
public final class Resolution {
    public enum Kind { SINGLE, MULTI, JDK, EXTERNAL }

    private final Kind kind;
    private final List<MethodKey> targets;

    private Resolution(Kind kind, List<MethodKey> targets) {
        this.kind = kind;
        this.targets = targets;
    }

    public static Resolution single(MethodKey m) { return new Resolution(Kind.SINGLE, Collections.singletonList(m)); }
    public static Resolution multi(List<MethodKey> ms) { return new Resolution(Kind.MULTI, Collections.unmodifiableList(ms)); }
    public static Resolution jdk() { return new Resolution(Kind.JDK, Collections.emptyList()); }
    /** 外部未知类：保留声明目标用于展示 */
    public static Resolution external(MethodKey declared) { return new Resolution(Kind.EXTERNAL, Collections.singletonList(declared)); }

    public Kind getKind() { return kind; }
    public List<MethodKey> getTargets() { return targets; }
}
