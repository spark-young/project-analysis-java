package com.spark.callgraph.service;

import com.spark.callgraph.engine.model.CallNode;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.AnalyzeRequest;
import com.spark.callgraph.service.dto.MethodFrequency;
import com.spark.callgraph.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计口径：验证去重后的独立方法数、被调次数(入度)与调用方采集正确。
 */
@SpringBootTest
class AnalysisStatsTest {

    private static final int TOP_N = 20;

    static Path demoClasses;

    @Autowired
    AnalysisService service;

    @BeforeAll
    static void setUp() throws Exception {
        Path libJar = Fixtures.compileToJar("lib");
        demoClasses = Fixtures.compileToDir("demo", libJar);
    }

    private AnalysisResult analyze(String method) {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setProjectPath(demoClasses.toString());
        req.setClassName("com.demo.OrderService");
        req.setMethodName(method);
        return service.analyze(req);
    }

    /** 收集一棵调用树里出现的全部节点方法（含重复，用于独立重算） */
    private void walk(CallNode node, Consumer<CallNode> sink) {
        sink.accept(node);
        for (CallNode c : node.getChildren()) walk(c, sink);
    }

    @Test
    void test_projectMethodsIsDistinct() {
        AnalysisResult r = analyze("place");
        // 独立重算：树里出现的全部不同 MethodKey 按 source 归类
        Set<MethodKey> distinct = new HashSet<>();
        Set<MethodKey> projectDistinct = new HashSet<>();
        for (CallNode root : r.getRoots()) walk(root, n -> {
            distinct.add(n.getMethod());
            if (n.getSource() == SourceType.PROJECT) projectDistinct.add(n.getMethod());
        });
        assertFalse(projectDistinct.isEmpty());
        // 去重口径 == 独立方法数，且小于重复计数的总节点数
        assertTrue(r.getStats().getProjectMethods() == projectDistinct.size(),
                "项目方法应为独立方法数, got=" + r.getStats().getProjectMethods()
                        + " expected=" + projectDistinct.size());
        assertTrue(r.getStats().getTotalNodes() > distinct.size(),
                "总节点(按节点计)应大于去重方法数");
        assertTrue(r.getStats().getProjectMethods() <= r.getStats().getTotalNodes());
    }

    @Test
    void test_callCountMatchesInDegree() {
        AnalysisResult r = analyze("place");
        // 独立重算每个方法的子节点出现次数（入度）
        Map<MethodKey, Integer> inDegree = new HashMap<>();
        for (CallNode root : r.getRoots()) walk(root, n -> {
            for (CallNode c : n.getChildren()) {
                inDegree.merge(c.getMethod(), 1, Integer::sum);
            }
        });
        // Util.trim：被 place(同级去重为1) + validate + pay(String) 调用
        MethodKey trim = MethodKey.of("com/demo/Util", "trim", "(Ljava/lang/String;)Ljava/lang/String;");
        Integer expected = inDegree.get(trim);
        assertTrue(expected != null && expected >= 3, "Util.trim 被调次数应≥3, got=" + expected);

        MethodFrequency f = findFrequency(r, trim);
        assertTrue(f != null, "Util.trim 应进入高频排行");
        assertTrue(f.getCallCount() == expected, "callCount 应等于入度, got=" + f.getCallCount()
                + " expected=" + expected);
        assertTrue(!f.getCallers().isEmpty(), "Util.trim 应有调用方");
        assertTrue(f.getCallers().size() <= TOP_N, "调用方数不得超过捕获上限");
    }

    @Test
    void test_callersContainPlaces() {
        AnalysisResult r = analyze("place");
        // 独立重算 Util.trim 的真实调用方集合
        Set<String> expectedCallers = new HashSet<>();
        for (CallNode root : r.getRoots()) walk(root, n -> {
            for (CallNode c : n.getChildren()) {
                if (c.getMethod().equals(MethodKey.of("com/demo/Util", "trim",
                        "(Ljava/lang/String;)Ljava/lang/String;"))) {
                    expectedCallers.add(n.getMethod().getIdentifier());
                }
            }
        });
        MethodFrequency f = findFrequency(r, MethodKey.of("com/demo/Util", "trim",
                "(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(f != null);
        for (com.spark.callgraph.service.dto.MethodCaller mc : f.getCallers()) {
            assertTrue(expectedCallers.contains(mc.getCaller()),
                    "调用方应来自独立重算集合: " + mc.getCaller());
        }
    }

    @Test
    void test_topN_limitedAndSorted() {
        AnalysisResult r = analyze("place");
        assertTrue(r.getMethodFrequency().size() <= TOP_N, "排行不得超过 Top N");
        for (int i = 0; i < r.getMethodFrequency().size(); i++) {
            MethodFrequency f = r.getMethodFrequency().get(i);
            assertTrue(f.getCallCount() > 0, "上榜方法被调次数须>0");
            if (i > 0) {
                MethodFrequency prev = r.getMethodFrequency().get(i - 1);
                assertTrue(prev.getCallCount() >= f.getCallCount(), "应按被调次数降序");
            }
        }
    }

    /** 在高频排行里找指定方法 */
    private static MethodFrequency findFrequency(AnalysisResult r, MethodKey key) {
        String id = key.getIdentifier();
        for (MethodFrequency f : r.getMethodFrequency()) {
            if (f.getMethod().equals(id)) return f;
        }
        return null;
    }
}