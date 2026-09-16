package com.spark.projectanalysis.engine;

import com.spark.projectanalysis.engine.model.CallGraph;
import com.spark.projectanalysis.engine.model.CallNode;
import com.spark.projectanalysis.engine.model.ClassInfo;
import com.spark.projectanalysis.engine.model.GraphMethod;
import com.spark.projectanalysis.engine.model.InvokeType;
import com.spark.projectanalysis.engine.model.MethodKey;
import com.spark.projectanalysis.engine.model.RawCall;
import com.spark.projectanalysis.engine.model.Resolution;
import com.spark.projectanalysis.engine.model.SourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调用图构建器：DFS + 路径内环检测 + 深度/节点上限 + 同级去重。
 * 语义：虚调用按静态接收类型解析；接口/抽象分派展示「声明节点 + 全部实现子节点」。
 *
 * <p>支持两种产出：</p>
 * <ul>
 *   <li>{@link #build}/{@link #buildRoots}：旧版树结构（渲染视图，仍可用作夹具/兼容）。</li>
 *   <li>{@link #buildGraph}/{@link #buildGraphRoots}：新版图结构（节点表+边表，瘦身上线）。</li>
 * </ul>
 */
public final class CallGraphBuilder {

    private final ClassMetadataRegistry registry;
    /** 同级去重表：from → 已挂载的 to 集合（单次构建生命周期内共享） */
    private final Map<Integer, Set<Integer>> seenEdgesPerParent = new HashMap<>();

    public CallGraphBuilder(ClassMetadataRegistry registry) {
        this.registry = registry;
    }

    /**
     * 为每个入口方法分配独立的节点预算。
     * 注意：不能用同一个 budget 数组串给所有入口——否则第一个大调用树会占光全部 MAX_NODES，
     * 导致后续入口一步入就预算耗尽、只剩孤根节点（Excel 里"只有第一个方法有链、其余为空"）。
     */
    public List<CallNode> buildRoots(List<MethodKey> roots, int maxDepth, int maxNodes) {
        List<CallNode> out = new ArrayList<>();
        for (MethodKey root : roots) {
            out.add(build(root, maxDepth, new int[]{maxNodes}));
        }
        return out;
    }

    public CallNode build(MethodKey root, int maxDepth, int maxNodes) {
        return build(root, maxDepth, new int[]{maxNodes});
    }

    private CallNode build(MethodKey method, int maxDepth, int[] budget) {
        CallNode rootNode = newNode(method, sourceOf(method), null, -1);
        budget[0]--;
        Set<MethodKey> path = new HashSet<>();
        path.add(method);
        expand(rootNode, 0, path, maxDepth, budget);
        return rootNode;
    }

    private void expand(CallNode node, int depth, Set<MethodKey> path, int maxDepth, int[] budget) {
        ClassInfo ci = registry.get(node.getMethod().getOwner());
        if (ci == null) return; // 外部类无字节码
        List<RawCall> calls = registry.callsOf(node.getMethod());
        if (calls.isEmpty()) return;
        if (depth >= maxDepth) {
            node.markTruncated();
            return;
        }
        for (RawCall raw : calls) {
            Resolution res = registry.resolve(raw.getOwner(), raw.getName(), raw.getDescriptor());
            switch (res.getKind()) {
                case JDK:
                    break;
                case EXTERNAL: {
                    CallNode ext = newNode(res.getTargets().get(0), SourceType.EXTERNAL,
                            raw.getInvokeType(), raw.getLine());
                    attach(node, ext, budget); // 不展开
                    break;
                }
                case SINGLE: {
                    MethodKey target = res.getTargets().get(0);
                    CallNode child = newNode(target, sourceOf(target), raw.getInvokeType(), raw.getLine());
                    if (attach(node, child, budget)) {
                        recurse(child, depth + 1, path, maxDepth, budget);
                    }
                    break;
                }
                case MULTI: {
                    // 声明节点（代码中实际书写的调用目标：接口/抽象方法）
                    MethodKey declared = MethodKey.of(raw.getOwner(), raw.getName(), raw.getDescriptor());
                    CallNode declaredNode = newNode(declared, sourceOf(declared), raw.getInvokeType(), raw.getLine());
                    if (attach(node, declaredNode, budget)) {
                        // 实现子节点位于声明节点下一层（物理深度 +2）
                        if (depth + 2 > maxDepth) {
                            declaredNode.markTruncated();
                        } else {
                            for (MethodKey impl : res.getTargets()) {
                                CallNode implNode = newNode(impl, sourceOf(impl), InvokeType.IMPL, raw.getLine());
                                if (attach(declaredNode, implNode, budget)) {
                                    recurse(implNode, depth + 2, path, maxDepth, budget);
                                }
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void recurse(CallNode node, int depth, Set<MethodKey> path, int maxDepth, int[] budget) {
        if (path.contains(node.getMethod())) {
            node.markCycle();
            return;
        }
        path.add(node.getMethod());
        expand(node, depth, path, maxDepth, budget);
        path.remove(node.getMethod());
    }

    /** 挂载子节点：同级去重 + 节点预算控制。返回是否实际挂载。 */
    private boolean attach(CallNode parent, CallNode child, int[] budget) {
        if (budget[0] <= 0) {
            parent.markTruncated();
            return false;
        }
        for (CallNode existing : parent.getChildren()) {
            if (existing.getMethod().equals(child.getMethod())) return false; // 同级去重
        }
        parent.getChildren().add(child);
        budget[0]--;
        return true;
    }

    private SourceType sourceOf(MethodKey method) {
        ClassInfo ci = registry.get(method.getOwner());
        return ci != null ? ci.getSource() : SourceType.EXTERNAL;
    }

    private static CallNode newNode(MethodKey method, SourceType source, InvokeType invokeType, int line) {
        return new CallNode(method, source, invokeType, line);
    }

    // ==================================================================
    // 图结构构建（新存储格式：去重节点表 + 边表）
    // ==================================================================

    /** 单个入口构建为一个图；为每个入口分配独立节点预算（与 buildRoots 相同的防串扰策略）。 */
    public CallGraph buildGraph(MethodKey root, int maxDepth, int maxNodes) {
        resetSharedState();
        CallGraph g = new CallGraph();
        int[] budget = {maxNodes};
        int rootId = ensureMethod(g, root, sourceOf(root), budget);
        g.getRoots().add(rootId);
        expandGraph(g, rootId, 0, new HashSet<>(), maxDepth, budget);
        return g;
    }

    /** 多个入口构建到同一个图（共享去重节点表），各自独立预算。 */
    public CallGraph buildGraphRoots(List<MethodKey> roots, int maxDepth, int maxNodes) {
        resetSharedState();
        CallGraph g = new CallGraph();
        for (MethodKey root : roots) {
            int[] budget = {maxNodes};
            int rootId = ensureMethod(g, root, sourceOf(root), budget);
            g.getRoots().add(rootId);
            expandGraph(g, rootId, 0, new HashSet<>(), maxDepth, budget);
        }
        return g;
    }

    private void resetSharedState() {
        seenEdgesPerParent.clear();
    }

    /** 图版 expand：语义与 {@link #expand} 完全一致，但把"挂子节点建树"改为"建边+去重节点"。 */
    private void expandGraph(CallGraph g, int callerId, int depth, Set<MethodKey> path,
                             int maxDepth, int[] budget) {
        MethodKey callerKey = g.getMethods().get(callerId).toKey();
        ClassInfo ci = registry.get(callerKey.getOwner());
        if (ci == null) return;                                      // 外部类无字节码
        List<RawCall> calls = registry.callsOf(callerKey);
        if (calls.isEmpty()) return;
        if (depth >= maxDepth) { g.setTruncated(true); return; }

        for (RawCall raw : calls) {
            Resolution res = registry.resolve(raw.getOwner(), raw.getName(), raw.getDescriptor());
            switch (res.getKind()) {
                case JDK:
                    break;
                case EXTERNAL: {
                    MethodKey target = res.getTargets().get(0);
                    int targetId = ensureMethod(g, target, SourceType.EXTERNAL, budget);
                    if (targetId >= 0) {
                        addEdgeDedup(g, callerId, targetId, raw);
                    }
                    break;                                          // 不展开
                }
                case SINGLE: {
                    MethodKey target = res.getTargets().get(0);
                    if (path.contains(target)) {                    // 环：方法级标记，不强建新节点/边
                        markCycle(g, target);
                        break;
                    }
                    int childId = ensureMethod(g, target, sourceOf(target), budget);
                    if (childId < 0) { g.setTruncated(true); continue; }
                    if (addEdgeDedup(g, callerId, childId, raw)) {
                        path.add(target);
                        expandGraph(g, childId, depth + 1, path, maxDepth, budget);
                        path.remove(target);
                    }
                    break;
                }
                case MULTI: {
                    MethodKey declared = MethodKey.of(raw.getOwner(), raw.getName(), raw.getDescriptor());
                    int declaredId = ensureMethod(g, declared, sourceOf(declared), budget);
                    if (declaredId < 0) { g.setTruncated(true); continue; }
                    if (addEdgeDedup(g, callerId, declaredId, raw)) {
                        if (depth + 2 > maxDepth) {
                            g.setTruncated(true);
                        } else {
                            for (MethodKey impl : res.getTargets()) {
                                if (path.contains(impl)) {          // 环：方法级标记
                                    markCycle(g, impl);
                                    continue;
                                }
                                int implChild = ensureMethod(g, impl, sourceOf(impl), budget);
                                if (implChild < 0) { g.setTruncated(true); continue; }
                                if (addEdgeDedup(g, declaredId, implChild, InvokeType.IMPL, raw.getLine())) {
                                    path.add(impl);
                                    expandGraph(g, implChild, depth + 2, path, maxDepth, budget);
                                    path.remove(impl);
                                }
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    /** 将方法标记为环（若节点已注册）。不强制新节点。 */
    private void markCycle(CallGraph g, MethodKey key) {
        for (int i = 0; i < g.getMethods().size(); i++) {
            GraphMethod m = g.getMethods().get(i);
            if (m.getOwner().equals(key.getOwner()) && m.getName().equals(key.getName())
                    && m.getDescriptor().equals(key.getDescriptor())) {
                m.setCycle(true);
                return;
            }
        }
    }

    /**
     * 注册方法并返回 id；预算耗尽返回 -1（调用方负责标记截断）。
     * 去重命中不耗预算；新增节点时递减 budget（根节点已在 buildGraph 提前占用 1）。
     */
    private int ensureMethod(CallGraph g, MethodKey key, SourceType source, int[] budget) {
        int existing = indexOfMethod(g, key);
        if (existing >= 0) return existing;                         // 去重命中：不耗预算
        if (budget[0] <= 0) return -1;                              // 预算耗尽
        budget[0]--;
        int id = g.getMethods().size();
        g.getMethods().add(GraphMethod.of(key, source));
        return id;
    }

    private int indexOfMethod(CallGraph g, MethodKey key) {
        for (int i = 0; i < g.getMethods().size(); i++) {
            GraphMethod m = g.getMethods().get(i);
            if (m.getOwner().equals(key.getOwner()) && m.getName().equals(key.getName())
                    && m.getDescriptor().equals(key.getDescriptor())) return i;
        }
        return -1;
    }

    /** 同级去重后挂边。同父→同被调只建一次。返回是否真正挂载。 */
    private boolean addEdgeDedup(CallGraph g, int from, int to, RawCall raw) {
        return addEdgeDedup(g, from, to, raw.getInvokeType(), raw.getLine());
    }

    private boolean addEdgeDedup(CallGraph g, int from, int to, InvokeType invoke, int line) {
        Set<Integer> seen = seenEdgesPerParent.computeIfAbsent(from, k -> new HashSet<>());
        if (!seen.add(to)) return false;                            // 同级去重（同父到同被调只建一次）
        g.addEdge(from, to, invoke, line);
        return true;
    }
}
