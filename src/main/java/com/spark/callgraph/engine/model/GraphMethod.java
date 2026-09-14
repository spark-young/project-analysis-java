package com.spark.callgraph.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 调用图中唯一方法节点（去重：同一方法全图只存一条）。
 * display 为渲染用派生串；className/simpleClassName 由前端按 owner 现拼，identifier 不再存储。
 */
public final class GraphMethod {
    private final String owner;      // ASM 内部名：com/foo/Bar
    private final String name;
    private final String descriptor;
    private final SourceType source;
    private final String display;
    private boolean cycle;           // 方法级环标记（构建期 DFS 判环置位）

    @JsonCreator
    public GraphMethod(@JsonProperty("owner") String owner,
                       @JsonProperty("name") String name,
                       @JsonProperty("descriptor") String descriptor,
                       @JsonProperty("source") SourceType source,
                       @JsonProperty("display") String display) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.source = source == null ? SourceType.EXTERNAL : source;
        this.display = display == null ? displayFallback() : display;
    }

    /** 放便捷构造：未显式给 display 时由 MethodKey 派生（取全限定标识，供渲染/导出直接使用） */
    public static GraphMethod of(MethodKey key, SourceType source) {
        return new GraphMethod(key.getOwner(), key.getName(), key.getDescriptor(), source, key.getIdentifier());
    }

    private String displayFallback() {
        return name + descriptor;
    }

    public MethodKey toKey() { return MethodKey.of(owner, name, descriptor); }

    public String getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescriptor() { return descriptor; }
    public SourceType getSource() { return source; }
    public String getDisplay() { return display; }
    public boolean isCycle() { return cycle; }
    public void setCycle(boolean cycle) { this.cycle = cycle; }

    @Override
    public String toString() { return owner + "#" + name + descriptor; }
}