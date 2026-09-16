package com.spark.projectanalysis.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 唯一标识一个方法：owner(内部名) + name + descriptor。
 */
public final class MethodKey {
    private final String owner;      // ASM 内部名：com/foo/Bar
    private final String name;
    private final String descriptor; // (I)Ljava/lang/String;

    @JsonCreator
    public MethodKey(@JsonProperty("owner") String owner,
                     @JsonProperty("name") String name,
                     @JsonProperty("descriptor") String descriptor) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public static MethodKey of(String owner, String name, String descriptor) {
        return new MethodKey(owner, name, descriptor);
    }

    public String getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescriptor() { return descriptor; }

    public String getClassName() { return owner.replace('/', '.'); }

    public String getSimpleClassName() {
        int i = owner.lastIndexOf('/');
        return i < 0 ? owner : owner.substring(i + 1);
    }

    /** 人可读展示：pay(String) / new Model() / static {}（getter 命名便于 JSON 序列化） */
    public String getDisplay() {
        if ("<init>".equals(name)) return "new " + getSimpleClassName() + paramList();
        if ("<clinit>".equals(name)) return "static {}";
        return name + paramList();
    }

    /** 全限定方法标识：com.foo.Bar#pay(String)；构造器为 com.foo.Bar#&lt;init&gt;(String) */
    public String getIdentifier() {
        return getClassName() + "#" + name + paramList();
    }

    private String paramList() {
        List<String> params = parseParams(descriptor);
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i));
        }
        return sb.append(")").toString();
    }

    /** 解析方法描述符为简短参数类型名列表 */
    static List<String> parseParams(String descriptor) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = descriptor.length();
        // 跳过参数声明之外的字符，从 '(' 后开始
        while (i < n && descriptor.charAt(i) != '(') i++;
        i++;
        while (i < n && descriptor.charAt(i) != ')') {
            StringBuilder type = new StringBuilder();
            while (i < n && descriptor.charAt(i) == '[') { type.append("[]"); i++; }
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                String cls = descriptor.substring(i + 1, semi);
                type.insert(0, shortClassName(cls));
                i = semi + 1;
            } else {
                type.insert(0, primitiveName(c));
                i++;
            }
            out.add(type.toString());
        }
        return out;
    }

    private static String shortClassName(String internal) {
        int slash = internal.lastIndexOf('/');
        int dollar = internal.lastIndexOf('$');
        int cut = Math.max(slash, dollar);
        return cut < 0 ? internal : internal.substring(cut + 1);
    }

    private static String primitiveName(char c) {
        switch (c) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'Z': return "boolean";
            default: return String.valueOf(c);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodKey)) return false;
        MethodKey that = (MethodKey) o;
        return owner.equals(that.owner) && name.equals(that.name) && descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() { return Objects.hash(owner, name, descriptor); }

    @Override
    public String toString() { return getClassName() + "." + name + descriptor; }
}
