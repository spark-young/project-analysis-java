package com.spark.projectanalysis.engine.entry;

import com.spark.projectanalysis.engine.ClassMetadataRegistry;
import com.spark.projectanalysis.engine.model.SourceType;
import com.spark.projectanalysis.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 入口探测器：REST / Dubbo / ElasticJob / main 四种。 */
class EntryPointDetectorsTest {

    static ClassMetadataRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        registry = ClassMetadataRegistry.builder()
                .addClassesDir(Fixtures.compileToDir("entries"), SourceType.PROJECT)
                .build();
    }

    @Test
    void test_restControllerDetector_mappedMethods() {
        List<EntryPoint> entries = new RestControllerDetector().detect(registry);

        Set<String> displays = entries.stream().map(EntryPoint::getDisplay).collect(Collectors.toSet());
        assertEquals(3, entries.size(), "list/place/remove 三个映射方法，helper 不算: " + displays);
        assertTrue(displays.contains("GET /api/orders/list"), "类级+方法级路径拼接: " + displays);
        assertTrue(displays.contains("POST /api/orders"), "空路径回退类级路径: " + displays);
        assertTrue(displays.contains("DELETE /api/orders/{id}"), "RequestMapping 的 method 枚举属性: " + displays);

        assertTrue(entries.stream().anyMatch(e ->
                e.getMethodName().equals("list") && e.getClassName().equals("com.demo.OrderController")
                        && e.getMethodDescriptor().equals("()Ljava/lang/String;")));
    }

    @Test
    void test_dubboServiceDetector_publicMethods() {
        List<EntryPoint> entries = new DubboServiceDetector().detect(registry);

        assertEquals(2, entries.size(), "pay + query 公共方法均为入口: " + entries);
        assertTrue(entries.stream().allMatch(e -> e.getDisplay().startsWith("dubbo: com.demo.api.PayApi#")),
                "interfaceClass 属性应识别 API 接口: " + entries);
        assertTrue(entries.stream().anyMatch(e -> e.getMethodName().equals("pay")));
        assertTrue(entries.stream().anyMatch(e -> e.getMethodName().equals("query")));
        assertTrue(entries.stream().allMatch(e -> e.getClassName().equals("com.demo.PayRpcImpl")));
    }

    @Test
    void test_elasticJobDetector_executeMethod() {
        List<EntryPoint> entries = new ElasticJobDetector().detect(registry);

        assertEquals(1, entries.size());
        assertEquals("com.demo.OrderSyncJob", entries.get(0).getClassName());
        assertEquals("execute", entries.get(0).getMethodName());
        assertTrue(entries.get(0).getDisplay().contains("OrderSyncJob"));
    }

    @Test
    void test_mainMethodDetector_publicStaticMain() {
        List<EntryPoint> entries = new MainMethodDetector().detect(registry);

        assertEquals(1, entries.size());
        assertEquals("com.demo.Launcher", entries.get(0).getClassName());
        assertEquals("main", entries.get(0).getMethodName());
    }
}
