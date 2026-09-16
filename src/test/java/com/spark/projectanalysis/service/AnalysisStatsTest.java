package com.spark.projectanalysis.service;

import com.spark.projectanalysis.engine.model.GraphEdge;
import com.spark.projectanalysis.engine.model.GraphMethod;
import com.spark.projectanalysis.engine.model.MethodKey;
import com.spark.projectanalysis.engine.model.SourceType;
import com.spark.projectanalysis.service.dto.AnalysisResult;
import com.spark.projectanalysis.service.dto.AnalyzeRequest;
import com.spark.projectanalysis.service.dto.MethodFrequency;
import com.spark.projectanalysis.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计口径：验证去重后（节点表）的独立方法数、被调次数(入边)与调用方采集正确。
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

    /** 图节点表即去重后的全部方法 */
    private static Set<MethodKey> distinctMethods(AnalysisResult r) {
        Set<MethodKey> distinct = new HashSet<>();
        for (GraphMethod m : r.getGraph().getMethods()) distinct.add(m.toKey());
        return distinct;
    }

    private static boolean isTrim(GraphMethod m) {
        return m.toKey().equals(MethodKey.of("com/demo/Util", "trim",
                "(Ljava/lang/String;)Ljava/lang/String;"));
    }

    @Test
    void test_projectMethodsIsDistinct() {
        AnalysisResult r = analyze("place");
        // 独立重算：图中全部方法按 source 归类
        Set<MethodKey> distinct = distinctMethods(r);
        Set<MethodKey> projectDistinct = new HashSet<>();
        for (GraphMethod m : r.getGraph().getMethods()) {
            if (m.getSource() == SourceType.PROJECT) projectDistinct.add(m.toKey());
        }
        assertFalse(projectDistinct.isEmpty());
        // 去重口径 == 独立方法数（节点表）；总节点 == 去重方法数（不再是重复计数）
        assertTrue(r.getStats().getProjectMethods() == projectDistinct.size(),
                "项目方法应为独立方法数, got=" + r.getStats().getProjectMethods()
                        + " expected=" + projectDistinct.size());
        assertTrue(r.getStats().getTotalNodes() == distinct.size(),
                "总节点应等于去重方法数, got=" + r.getStats().getTotalNodes() + " methods=" + distinct.size());
        assertTrue(r.getStats().getProjectMethods() <= r.getStats().getTotalNodes());
    }

    @Test
    void test_callCountMatchesInDegree() {
        AnalysisResult r = analyze("place");
        // 独立重算每个方法的入边数（不同调用方→该方法 的边，构建期已同级去重）
        Map<MethodKey, Integer> inDegree = new HashMap<>();
        for (GraphEdge e : r.getGraph().getEdges()) {
            GraphMethod target = r.getGraph().getMethods().get(e.getTo());
            inDegree.merge(target.toKey(), 1, Integer::sum);
        }
        MethodKey trim = MethodKey.of("com/demo/Util", "trim", "(Ljava/lang/String;)Ljava/lang/String;");
        Integer expected = inDegree.get(trim);
        assertTrue(expected != null && expected >= 2, "Util.trim 入边(不同调用方)应≥2, got=" + expected);

        MethodFrequency f = findFrequency(r, trim);
        assertTrue(f != null, "Util.trim 应进入高频排行");
        assertTrue(f.getCallCount() == expected, "callCount 应等于入边数, got=" + f.getCallCount()
                + " expected=" + expected);
        assertTrue(!f.getCallers().isEmpty(), "Util.trim 应有调用方");
        assertTrue(f.getCallers().size() <= TOP_N, "调用方数不得超过捕获上限");
    }

    @Test
    void test_callersContainPlaces() {
        AnalysisResult r = analyze("place");
        // 独立重算 Util.trim 的真实调用方集合（以边表为准）
        Set<String> expectedCallers = new HashSet<>();
        for (GraphEdge e : r.getGraph().getEdges()) {
            GraphMethod target = r.getGraph().getMethods().get(e.getTo());
            if (isTrim(target)) {
                expectedCallers.add(r.getGraph().getMethods().get(e.getFrom()).toKey().getIdentifier());
            }
        }
        MethodFrequency f = findFrequency(r, MethodKey.of("com/demo/Util", "trim",
                "(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(f != null);
        assertTrue(!expectedCallers.isEmpty(), "Util.trim 应有独立重算调用方");
        for (com.spark.projectanalysis.service.dto.MethodCaller mc : f.getCallers()) {
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