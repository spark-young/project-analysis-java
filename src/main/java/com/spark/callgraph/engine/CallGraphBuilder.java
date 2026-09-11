package com.spark.callgraph.engine;

import com.spark.callgraph.engine.model.CallNode;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.InvokeType;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.RawCall;
import com.spark.callgraph.engine.model.Resolution;
import com.spark.callgraph.engine.model.SourceType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 调用树构建器：DFS + 路径内环检测 + 深度/节点上限 + 同级去重。
 * 语义：虚调用按静态接收类型解析；接口/抽象分派展示「声明节点 + 全部实现子节点」。
 */
public final class CallGraphBuilder {

    private final ClassMetadataRegistry registry;

    public CallGraphBuilder(ClassMetadataRegistry registry) {
        this.registry = registry;
    }

    public List<CallNode> buildRoots(List<MethodKey> roots, int maxDepth, int maxNodes) {
        List<CallNode> out = new ArrayList<>();
        int[] budget = {maxNodes};
        for (MethodKey root : roots) {
            out.add(build(root, maxDepth, budget));
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
}
