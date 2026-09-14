package com.spark.callgraph.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用图：去重节点表 + 调用边表 + 根索引。
 * 语义：每个方法（owner+name+descriptor）全图只出现一次（methods 下标即 id），
 * 调用关系存为边。环以方法级 {@link GraphMethod#isCycle()} 标记。
 */
public final class CallGraph {
    private final List<GraphMethod> methods = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final List<Integer> roots = new ArrayList<>();
    private boolean truncated;   // 因深度/节点预算截断（图级别）

    @JsonCreator
    public CallGraph(@JsonProperty("methods") List<GraphMethod> methods,
                     @JsonProperty("edges") List<GraphEdge> edges,
                     @JsonProperty("roots") List<Integer> roots) {
        if (methods != null) this.methods.addAll(methods);
        if (edges != null) this.edges.addAll(edges);
        if (roots != null) this.roots.addAll(roots);
        adjacencyDirty = true;                                      // 反序列化后惰性重建邻接表
    }

    public CallGraph() {}

    // ---------- 邻接表（渲染/统计/导出共用；惰性构建，任一边表状态变化后重建） ----------
    private transient Map<Integer, List<GraphEdge>> adjacency = new HashMap<>();
    private transient boolean adjacencyDirty = false;

    /** 记录边表已变更（惰性重建邻接表） */
    public void invalidateAdjacency() { adjacencyDirty = true; }

    private void ensureAdjacency() {
        if (!adjacencyDirty && !adjacency.isEmpty()) return;
        adjacency = new HashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            adjacency.computeIfAbsent(edges.get(i).getFrom(), k -> new ArrayList<>()).add(edges.get(i));
        }
        adjacencyDirty = false;
    }

    /** 取某节点的调用边集合（无调用返回空表） */
    public List<GraphEdge> edgesOf(int methodId) {
        ensureAdjacency();
        return adjacency.getOrDefault(methodId, new ArrayList<>());
    }

    /** 取某节点的被调方法 id 集合（供前端递归渲染） */
    public List<Integer> childrenOf(int methodId) {
        List<Integer> out = new ArrayList<>();
        for (GraphEdge e : edgesOf(methodId)) out.add(e.getTo());
        return out;
    }

    /** 注册一个方法，返回其 id（已存在则原样返回） */
    public int ensureMethod(GraphMethod m) {
        for (int i = 0; i < methods.size(); i++) {
            GraphMethod existing = methods.get(i);
            if (existing.getOwner().equals(m.getOwner())
                    && existing.getName().equals(m.getName())
                    && existing.getDescriptor().equals(m.getDescriptor())) {
                return i;
            }
        }
        methods.add(m);
        return methods.size() - 1;
    }

    /** 追加一条边（from → to）。同级去重由调用方保证。 */
    public void addEdge(int from, int to, InvokeType invoke, int line) {
        edges.add(new GraphEdge(from, to, invoke, line));
        adjacencyDirty = true;
    }

    public List<GraphMethod> getMethods() { return methods; }
    public List<GraphEdge> getEdges() { return edges; }
    public List<Integer> getRoots() { return roots; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    @Override
    public String toString() {
        return "CallGraph{methods=" + methods.size() + ", edges=" + edges.size()
                + ", roots=" + roots + '}';
    }
}