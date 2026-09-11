package com.spark.callgraph.engine;

import com.spark.callgraph.engine.model.CallNode;
import com.spark.callgraph.engine.model.InvokeType;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调用树构建核心用例（夹具：真实 javac 编译产物）。
 */
class CallGraphBuilderTest {

    static Path libJar;
    static Path demoClasses;
    static ClassMetadataRegistry registry;      // demo(项目) + lib(依赖)
    static ClassMetadataRegistry registryNoLib;  // 仅 demo（模拟缺失依赖）

    @BeforeAll
    static void setUp() throws Exception {
        libJar = Fixtures.compileToJar("lib");
        demoClasses = Fixtures.compileToDir("demo", libJar);
        registry = ClassMetadataRegistry.builder()
                .addClassesDir(demoClasses, SourceType.PROJECT)
                .addJar(libJar, SourceType.DEPENDENCY)
                .build();
        registryNoLib = ClassMetadataRegistry.builder()
                .addClassesDir(demoClasses, SourceType.PROJECT)
                .build();
    }

    private CallNode place() {
        return new CallGraphBuilder(registry).build(MethodKey.of("com/demo/OrderService", "place", "()V"), 20, 100000);
    }

    // ---------- 遍历辅助 ----------

    static CallNode childByName(CallNode parent, String name) {
        Optional<CallNode> hit = parent.getChildren().stream()
                .filter(c -> c.getMethod().getName().equals(name)).findFirst();
        assertTrue(hit.isPresent(), "未找到直接子节点: " + name + "（父: " + parent.getMethod() + "）");
        return hit.get();
    }

    static List<CallNode> childrenByName(CallNode parent, String name) {
        List<CallNode> out = new ArrayList<>();
        for (CallNode c : parent.getChildren()) {
            if (c.getMethod().getName().equals(name)) out.add(c);
        }
        return out;
    }

    static Optional<CallNode> findFirst(CallNode node, Predicate<CallNode> p) {
        if (p.test(node)) return Optional.of(node);
        for (CallNode c : node.getChildren()) {
            Optional<CallNode> r = findFirst(c, p);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    static List<CallNode> allNodes(CallNode node) {
        List<CallNode> out = new ArrayList<>();
        collect(node, out);
        return out;
    }

    private static void collect(CallNode node, List<CallNode> out) {
        out.add(node);
        for (CallNode c : node.getChildren()) collect(c, out);
    }

    // ---------- 用例 ----------

    @Test
    void test_place_containsDirectVirtualSelfCall() {
        CallNode validate = childByName(place(), "validate");
        assertEquals(InvokeType.VIRTUAL, validate.getInvokeType());
        assertEquals(SourceType.PROJECT, validate.getSource());
    }

    @Test
    void test_place_containsStaticCall_andDedupSameLevel() {
        CallNode root = place();
        CallNode now = childByName(root, "now");
        assertEquals(InvokeType.STATIC, now.getInvokeType());
        assertEquals("com/demo/Util", now.getMethod().getOwner());
        // place 中调用了两次 Util.trim —— 同级去重为一个节点
        assertEquals(1, childrenByName(root, "trim").size());
    }

    @Test
    void test_place_interfaceDispatch_multipleImplementations() {
        CallNode greet = childByName(place(), "greet");
        assertEquals("com/demo/Greeter", greet.getMethod().getOwner());
        assertEquals(InvokeType.INTERFACE, greet.getInvokeType());
        List<CallNode> impls = greet.getChildren();
        assertEquals(2, impls.size());
        for (CallNode impl : impls) {
            assertEquals(InvokeType.IMPL, impl.getInvokeType());
        }
        assertTrue(impls.stream().anyMatch(c -> c.getMethod().getOwner().equals("com/demo/EnglishGreeter")));
        assertTrue(impls.stream().anyMatch(c -> c.getMethod().getOwner().equals("com/demo/ChineseGreeter")));
    }

    @Test
    void test_place_interfaceDispatch_singleImplementation() {
        CallNode save = childByName(place(), "save");
        assertEquals("com/demo/Repo", save.getMethod().getOwner());
        assertEquals(InvokeType.INTERFACE, save.getInvokeType());
        List<CallNode> impls = save.getChildren();
        assertEquals(1, impls.size());
        assertEquals("com/demo/DbRepo", impls.get(0).getMethod().getOwner());
        // 实现方法继续向下展开
        assertFalse(impls.get(0).getChildren().isEmpty(), "DbRepo.save 应继续展开其内部调用");
    }

    @Test
    void test_place_overloadResolution() {
        CallNode root = place();
        List<CallNode> pays = childrenByName(root, "pay");
        assertEquals(2, pays.size());
        // pay(String) -> Util.trim ；pay(int) -> Util.now
        boolean[] expanded = new boolean[2];
        for (CallNode pay : pays) {
            boolean callsTrim = pay.getChildren().stream().anyMatch(c -> c.getMethod().getName().equals("trim"));
            boolean callsNow = pay.getChildren().stream().anyMatch(c -> c.getMethod().getName().equals("now"));
            if (pay.getMethod().getDescriptor().startsWith("(Ljava/lang/String;)")) {
                assertTrue(callsTrim, "pay(String) 应调用 trim");
            } else if (pay.getMethod().getDescriptor().startsWith("(I)")) {
                assertTrue(callsNow, "pay(int) 应调用 now");
            } else {
                fail("未知重载: " + pay.getMethod());
            }
        }
    }

    @Test
    void test_place_lambdaResolution() {
        CallNode root = place();
        Optional<CallNode> lambda = findFirst(root, n -> n.getMethod().getName().startsWith("lambda$place$"));
        assertTrue(lambda.isPresent(), "应解析出 lambda 合成方法");
        assertEquals(InvokeType.DYNAMIC, lambda.get().getInvokeType());
        // lambda 体内部调用 log
        assertTrue(lambda.get().getChildren().stream()
                .anyMatch(c -> c.getMethod().getName().equals("log")), "lambda 体应调用 log");
    }

    @Test
    void test_methodReference_resolution() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "methodRef", "()V"), 20, 100000);
        CallNode log = childByName(root, "log");
        assertEquals(InvokeType.DYNAMIC, log.getInvokeType());
    }

    @Test
    void test_jdkMethodsExcluded() {
        List<CallNode> all = allNodes(place());
        for (CallNode n : all) {
            String owner = n.getMethod().getOwner();
            assertFalse(owner.startsWith("java/") || owner.startsWith("javax/")
                            || owner.startsWith("sun/") || owner.startsWith("jdk/"),
                    "JDK 方法不应出现: " + n.getMethod());
        }
    }

    @Test
    void test_selfRecursion_markedAsCycle() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "loop", "()V"), 20, 100000);
        CallNode child = childByName(root, "loop");
        assertTrue(child.isCycle());
        assertTrue(child.getChildren().isEmpty(), "环节点不应继续展开");
    }

    @Test
    void test_mutualRecursion_terminated() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "ping", "()V"), 20, 100000);
        CallNode pong = childByName(root, "pong");
        CallNode pingAgain = childByName(pong, "ping");
        assertTrue(pingAgain.isCycle());
    }

    @Test
    void test_constructorChain() {
        CallNode root = place();
        Optional<CallNode> init = findFirst(root, n ->
                n.getMethod().getName().equals("<init>") && n.getMethod().getOwner().equals("com/demo/Model"));
        assertTrue(init.isPresent(), "应出现 Model 构造方法节点");
        assertEquals(InvokeType.SPECIAL, init.get().getInvokeType());
        // super Object.<init> 属 JDK —— 不出现
        assertTrue(init.get().getChildren().isEmpty(), "Object.<init> 应被排除");
    }

    @Test
    void test_abstractMethodDispatch() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "describe", "(Lcom/demo/AbstractBase;)Ljava/lang/String;"), 20, 100000);
        CallNode format = childByName(root, "format");
        assertEquals("com/demo/AbstractBase", format.getMethod().getOwner());
        // 抽象声明节点（AbstractBase.describe）+ 具体实现子节点（SubService.describe）
        CallNode describeDeclared = childByName(format, "describe");
        assertEquals("com/demo/AbstractBase", describeDeclared.getMethod().getOwner());
        CallNode describeImpl = childByName(describeDeclared, "describe");
        assertEquals("com/demo/SubService", describeImpl.getMethod().getOwner());
        assertEquals(InvokeType.IMPL, describeImpl.getInvokeType());
    }

    @Test
    void test_concreteTypedCall_directResolution() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "direct", "(Lcom/demo/DbRepo;)V"), 20, 100000);
        CallNode save = childByName(root, "save");
        assertEquals("com/demo/DbRepo", save.getMethod().getOwner());
        assertEquals(InvokeType.VIRTUAL, save.getInvokeType(), "具体类型接收者应按虚调用解析");
    }

    @Test
    void test_dependencyMethod_expanded() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "withLib", "(Lcom/demo/lib/GreeterService;)V"), 20, 100000);
        CallNode serve = childByName(root, "serve");
        assertEquals(SourceType.DEPENDENCY, serve.getSource());
        CallNode decorate = childByName(serve, "decorate");
        assertEquals(SourceType.DEPENDENCY, decorate.getSource());
    }

    @Test
    void test_unknownClass_shownAsExternalLeaf() {
        CallNode root = new CallGraphBuilder(registryNoLib)
                .build(MethodKey.of("com/demo/OrderService", "withLib", "(Lcom/demo/lib/GreeterService;)V"), 20, 100000);
        CallNode serve = childByName(root, "serve");
        assertEquals(SourceType.EXTERNAL, serve.getSource());
        assertTrue(serve.getChildren().isEmpty(), "外部类应作为叶子节点");
    }

    @Test
    void test_depthLimit() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "place", "()V"), 1, 100000);
        List<CallNode> all = allNodes(root);
        for (CallNode n : all) {
            assertTrue(depth(root, n, 0) <= 1);
        }
        // 深一层节点被截断标记
        assertTrue(all.stream().anyMatch(CallNode::isTruncated), "应有截断标记");
    }

    private int depth(CallNode root, CallNode target, int d) {
        if (root == target) return d;
        for (CallNode c : root.getChildren()) {
            int r = depth(c, target, d + 1);
            if (r >= 0) return r;
        }
        return -1;
    }

    @Test
    void test_nodeCap() {
        CallNode root = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "place", "()V"), 20, 5);
        List<CallNode> all = allNodes(root);
        assertTrue(all.size() <= 5, "总节点数不得超过上限: " + all.size());
        assertTrue(all.size() >= 3);
    }

    @Test
    void test_buildRoots_classLevelAnalysis() {
        List<MethodKey> roots = new ArrayList<>();
        roots.add(MethodKey.of("com/demo/OrderService", "place", "()V"));
        roots.add(MethodKey.of("com/demo/OrderService", "loop", "()V"));
        List<CallNode> trees = new CallGraphBuilder(registry).buildRoots(roots, 20, 100000);
        assertEquals(2, trees.size());
        assertEquals("place", trees.get(0).getMethod().getName());
        assertTrue(trees.get(0).getInvokeType() == null, "根节点无调用方式");
    }
}
