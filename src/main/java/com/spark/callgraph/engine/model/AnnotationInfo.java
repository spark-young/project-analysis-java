package com.spark.callgraph.engine.model;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解元数据：注解内部名 + 属性值（属性名 -> 值列表）。
 * 值统一记录为字符串：String 原样、枚举记录常量名、Class 记录类型描述符（如 Lcom/foo/Bar;）。
 */
public final class AnnotationInfo {

    private final String internalName;
    private final Map<String, List<String>> values;

    public AnnotationInfo(String internalName, Map<String, List<String>> values) {
        this.internalName = internalName;
        this.values = new LinkedHashMap<>(values);
    }

    public String getInternalName() { return internalName; }

    /** 属性首个值；无该属性返回 null */
    public String first(String attr) {
        List<String> v = values.get(attr);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** 属性全部值（数组属性）；无返回空列表 */
    public List<String> all(String attr) {
        List<String> v = values.get(attr);
        return v == null ? Collections.<String>emptyList() : Collections.unmodifiableList(v);
    }

    public boolean hasAttribute(String attr) {
        return values.containsKey(attr);
    }

    @Override
    public String toString() {
        return "AnnotationInfo{" + internalName + " " + values + "}";
    }

    /**
     * ASM 注解访问器：构建期收集注解属性。
     * visitArray 元素以 visit(null, v) / visitEnum(null, ...) 回调，追加到数组属性。
     */
    public static final class Collector extends AnnotationVisitor {

        private final String internalName;
        private final Map<String, List<String>> values = new LinkedHashMap<>();

        public Collector(int asmApi, String descriptor) {
            super(asmApi);
            this.internalName = normalize(descriptor);
        }

        private static String normalize(String descriptor) {
            if (descriptor == null) return "";
            if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                return descriptor.substring(1, descriptor.length() - 1);
            }
            return descriptor;
        }

        @Override
        public void visit(String name, Object value) {
            add(name, value == null ? null : value.toString());
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            add(name, value);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            final String attr = name;
            return new AnnotationVisitor(api) {
                @Override
                public void visit(String unused, Object value) {
                    add(attr, value == null ? null : value.toString());
                }

                @Override
                public void visitEnum(String unused, String descriptor, String value) {
                    add(attr, value);
                }
            };
        }

        private synchronized void add(String name, String value) {
            if (name == null || value == null) return;
            values.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        public AnnotationInfo build() {
            return new AnnotationInfo(internalName, values);
        }
    }
}
