package com.spark.projectanalysis.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类元数据（Phase A 用 SKIP_CODE 解析所得，不含方法体）。
 */
public final class ClassInfo {
    private final String internalName;
    private final String superName;      // java/lang/Object 为 null 时表示未知/无
    private final List<String> interfaces;
    private final int access;
    private final Map<String, Integer> methods; // key: name + descriptor -> access
    private final SourceType source;
    private final String location;      // 来源 jar 路径或 classes 目录路径（Phase B 按需重解析用）
    private final List<AnnotationInfo> annotations;                       // 类级注解
    private final Map<String, List<AnnotationInfo>> methodAnnotations;    // key: name + descriptor

    public ClassInfo(String internalName, String superName, List<String> interfaces,
                     int access, Map<String, Integer> methods, SourceType source, String location) {
        this(internalName, superName, interfaces, access, methods, source, location,
                null, null);
    }

    public ClassInfo(String internalName, String superName, List<String> interfaces,
                     int access, Map<String, Integer> methods, SourceType source, String location,
                     List<AnnotationInfo> annotations,
                     Map<String, List<AnnotationInfo>> methodAnnotations) {
        this.internalName = internalName;
        this.superName = superName;
        this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces));
        this.access = access;
        this.methods = new LinkedHashMap<>(methods);
        this.source = source;
        this.location = location;
        this.annotations = annotations == null
                ? Collections.<AnnotationInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(annotations));
        this.methodAnnotations = methodAnnotations == null
                ? Collections.<String, List<AnnotationInfo>>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(methodAnnotations));
    }

    public String getInternalName() { return internalName; }
    public String getSuperName() { return superName; }
    public List<String> getInterfaces() { return interfaces; }
    public int getAccess() { return access; }
    public SourceType getSource() { return source; }
    public String getLocation() { return location; }

    /** 类级注解列表 */
    public List<AnnotationInfo> getAnnotations() { return annotations; }

    /** 按内部名取类级注解；无返回 null */
    public AnnotationInfo annotation(String annotationInternalName) {
        for (AnnotationInfo a : annotations) {
            if (a.getInternalName().equals(annotationInternalName)) return a;
        }
        return null;
    }

    /** 方法级注解列表 */
    public List<AnnotationInfo> methodAnnotations(String name, String descriptor) {
        List<AnnotationInfo> list = methodAnnotations.get(name + descriptor);
        return list == null ? Collections.<AnnotationInfo>emptyList() : list;
    }

    /** 按内部名取方法级注解；无返回 null */
    public AnnotationInfo methodAnnotation(String name, String descriptor, String annotationInternalName) {
        for (AnnotationInfo a : methodAnnotations(name, descriptor)) {
            if (a.getInternalName().equals(annotationInternalName)) return a;
        }
        return null;
    }

    public boolean isInterface() { return (access & 0x0200) != 0; } // ACC_INTERFACE
    public boolean isAbstract() { return (access & 0x0400) != 0; }   // ACC_ABSTRACT

    public boolean hasOwnMethod(String name, String descriptor) {
        return methods.containsKey(name + descriptor);
    }

    /** 方法在类内有具体实现（非 abstract / 非 native） */
    public boolean isMethodConcrete(String name, String descriptor) {
        Integer a = methods.get(name + descriptor);
        return a != null && (a & 0x0400) == 0 && (a & 0x0100) == 0; // 非 ACC_ABSTRACT、非 ACC_NATIVE
    }

    /** 方法 access 标志；未知返回 0 */
    public int methodAccess(String name, String descriptor) {
        Integer a = methods.get(name + descriptor);
        return a != null ? a : 0;
    }

    /** 本类声明的全部方法 */
    public List<MethodKey> methodKeys() {
        List<MethodKey> out = new ArrayList<>();
        for (String key : methods.keySet()) {
            out.add(split(key));
        }
        return out;
    }

    private MethodKey split(String namePlusDesc) {
        int paren = namePlusDesc.indexOf('(');
        return MethodKey.of(internalName, namePlusDesc.substring(0, paren), namePlusDesc.substring(paren));
    }
}
