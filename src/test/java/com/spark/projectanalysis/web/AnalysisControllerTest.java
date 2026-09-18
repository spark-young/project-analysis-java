package com.spark.projectanalysis.web;

import com.spark.projectanalysis.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层全流程：真实编译夹具 → REST API → 断言 JSON/Excel。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerTest {

    static Path demoClasses;
    static Path entriesClasses;

    @BeforeAll
    static void setUp() throws Exception {
        Path libJar = Fixtures.compileToJar("lib");
        demoClasses = Fixtures.compileToDir("demo", libJar);
        entriesClasses = Fixtures.compileToDir("entries");
    }

    @Autowired
    MockMvc mvc;

    private String body(String path, String className, String methodName) {
        return "{\"projectPath\":\"" + escapeWindows(path)
                + "\",\"className\":\"" + className + "\",\"methodName\":\"" + methodName + "\"}";
    }

    private static String escapeWindows(String s) {
        return s.replace("\\", "\\\\");
    }

    @Test
    void test_analyzeMethod_returnsTree() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "com.demo.OrderService", "place")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("com.demo.OrderService"))
                .andExpect(jsonPath("$.methodName").value("place"))
                .andExpect(jsonPath("$.schema").value(2))
                .andExpect(jsonPath("$.graph.roots.length()").value(1))
                .andExpect(jsonPath("$.graph.methods[0].name").value("place"))
                .andExpect(jsonPath("$.graph.methods[0].display")
                        .value("com.demo.OrderService#place()"))
                .andExpect(jsonPath("$.stats.totalNodes").value(
                        org.hamcrest.Matchers.greaterThan(10)))
                .andExpect(jsonPath("$.stats.projectMethods").value(
                        org.hamcrest.Matchers.greaterThan(5)))
                // 高频排行存在、非空、被调次数为正
                .andExpect(jsonPath("$.methodFrequency").isNotEmpty())
                .andExpect(jsonPath("$.methodFrequency[0].callCount")
                        .value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void test_analyzeWholeClass_multipleRoots() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "com.demo.OrderService", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.roots.length()").value(
                        org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$.methodName").doesNotExist());
    }

    @Test
    void test_simpleClassName_uniqueMatch() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "OrderService", "loop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("com.demo.OrderService"))
                .andExpect(jsonPath("$.graph.roots.length()").value(1))
                .andExpect(jsonPath("$.graph.methods[0].name").value("loop"))
                .andExpect(jsonPath("$.graph.methods[0].cycle").value(true));
    }

    @Test
    void test_badPath_rejected() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body("X:/no/such/dir", "Foo", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void test_unknownClass_notFound() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "com.nosuch.Foo", "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void test_unknownMethod_notFoundWithAvailableList() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "com.demo.OrderService", "noSuchMethod")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("place")));
    }

    @Test
    void test_projectInfo() throws Exception {
        mvc.perform(get("/api/project/info").param("path", demoClasses.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutType").value("RAW_CLASSES"))
                .andExpect(jsonPath("$.projectClassCount").value(
                        org.hamcrest.Matchers.greaterThan(8)));
    }

    @Test
    void test_classesSearch() throws Exception {
        mvc.perform(get("/api/classes/search")
                        .param("path", demoClasses.toString())
                        .param("q", "Order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("com.demo.OrderService"));
    }

    @Test
    void test_excelDownload() throws Exception {
        MvcResult result = mvc.perform(post("/api/report/excel").contentType(MediaType.APPLICATION_JSON)
                        .content(body(demoClasses.toString(), "com.demo.OrderService", "place")))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        byte[] body = result.getResponse().getContentAsByteArray();
        assertTrue(body.length > 1000, "Excel 应有实际内容");
        // xlsx 为 zip 格式，魔数 PK
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains("attachment"));
    }

    // ------------------------------------------------------------------
    // 多入口分析
    // ------------------------------------------------------------------

    @Test
    void test_analyzeSelectedEntries_multiRoots() throws Exception {
        String entriesBody = "{\"projectPath\":\"" + escapeWindows(entriesClasses.toString()) + "\",\"entries\":["
                + "{\"className\":\"com.demo.OrderController\",\"methodName\":\"list\","
                + "\"methodDescriptor\":\"()Ljava/lang/String;\"},"
                + "{\"className\":\"com.demo.Launcher\",\"methodName\":\"main\"}"
                + "]}";
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(entriesBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.roots.length()").value(2))
                .andExpect(jsonPath("$.graph.methods[?(@.name == 'list')]").isNotEmpty())
                .andExpect(jsonPath("$.graph.methods[?(@.name == 'main')]").isNotEmpty())
                .andExpect(jsonPath("$.stats.entryCount").value(2))
                .andExpect(jsonPath("$.className").doesNotExist());
    }

    @Test
    void test_excelForEntries() throws Exception {
        String entriesBody = "{\"projectPath\":\"" + escapeWindows(entriesClasses.toString()) + "\",\"entries\":["
                + "{\"className\":\"com.demo.OrderController\",\"methodName\":\"list\"},"
                + "{\"className\":\"com.demo.Launcher\",\"methodName\":\"main\"}"
                + "]}";
        MvcResult result = mvc.perform(post("/api/report/excel").contentType(MediaType.APPLICATION_JSON)
                        .content(entriesBody))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
        assertTrue(body.length > 1000, "Excel 应有实际内容");
    }

    // ------------------------------------------------------------------
    // 全局异常处理（OPT-11）
    // ------------------------------------------------------------------

    @Test
    void test_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"bad\": json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void test_unsupportedMethod_returns405() throws Exception {
        // /api/analyze 仅接受 POST；发 GET 应返回 405
        mvc.perform(get("/api/analyze"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------
    // GET /api/classes/verify（OPT-28 切片1：Map → EntryVerifyResult DTO 化，
    // 以下断言为 JSON 形状契约锁：字段名/出现条件与 Map 版本逐字一致）
    // ------------------------------------------------------------------

    /** 类名为空：ok=false, reason=类名不能为空，其余字段不应出现（non_null 省略 = 原 Map 无该键） */
    @Test
    void test_verify_blankClassName() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").value("类名不能为空"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 类不存在：ok=false, reason 前缀固定，无附加字段 */
    @Test
    void test_verify_classNotFound() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.nosuch.Foo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").value("项目中未找到类: com.nosuch.Foo"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 简单名重复（entries 夹具 2 个 Service）：ok=false + candidates（2 项全限定名，顺序不敏感） */
    @Test
    void test_verify_duplicateSimpleName_returnsCandidates() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", entriesClasses.toString())
                        .param("class", "Service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason")
                        .value("找到 2 个同名类，请填全限定名"))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasItem(
                        "com.alibaba.dubbo.config.annotation.Service")))
                .andExpect(jsonPath("$.candidates", org.hamcrest.Matchers.hasItem(
                        "org.apache.dubbo.config.annotation.Service")))
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 类存在、未传 method：ok=true + reason=类存在: xxx，无其他字段 */
    @Test
    void test_verify_classOnly() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reason").value("类存在: com.demo.OrderService"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 方法不存在：ok=false + available 列出可选方法名（去重后集合，顺序不敏感） */
    @Test
    void test_verify_methodNotFound_returnsAvailable() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService")
                        .param("method", "noSuchMethod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").value("类存在但方法 noSuchMethod 不存在"))
                .andExpect(jsonPath("$.available", org.hamcrest.Matchers.hasItem("place")))
                .andExpect(jsonPath("$.available", org.hamcrest.Matchers.hasItem("pay")))
                .andExpect(jsonPath("$.available", org.hamcrest.Matchers.hasItem("loop")))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 唯一重载：ok=true + descriptor 给出建议值，无 multipleOverloads */
    @Test
    void test_verify_uniqueOverload_returnsDescriptor() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService")
                        .param("method", "place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reason")
                        .value("✓ 已匹配唯一重载，建议 descriptor: ()V"))
                .andExpect(jsonPath("$.descriptor").value("()V"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** 多重载：ok=true + multipleOverloads 共 2 个 descriptor，无 descriptor 字段 */
    @Test
    void test_verify_multipleOverloads() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService")
                        .param("method", "pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reason")
                        .value("✓ 方法存在但有 2 个重载，建议指定 descriptor 精确匹配"))
                .andExpect(jsonPath("$.multipleOverloads.length()").value(2))
                .andExpect(jsonPath("$.multipleOverloads", org.hamcrest.Matchers.hasItem(
                        "(Ljava/lang/String;)V")))
                .andExpect(jsonPath("$.multipleOverloads", org.hamcrest.Matchers.hasItem("(I)V")))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist());
    }

    /** class+method+descriptor 完整匹配：ok=true, reason 前缀 ✓ 完整匹配 */
    @Test
    void test_verify_exactDescriptorMatch() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService")
                        .param("method", "pay")
                        .param("descriptor", "(I)V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reason")
                        .value("✓ 完整匹配: com.demo.OrderService#pay(I)V"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }

    /** descriptor 不匹配：ok=false, reason=类存在，但未找到方法 ...，无附加字段 */
    @Test
    void test_verify_descriptorMismatch() throws Exception {
        mvc.perform(get("/api/classes/verify")
                        .param("path", demoClasses.toString())
                        .param("class", "com.demo.OrderService")
                        .param("method", "pay")
                        .param("descriptor", "(Z)V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason")
                        .value("类存在，但未找到方法 pay(Z)V"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.descriptor").doesNotExist())
                .andExpect(jsonPath("$.multipleOverloads").doesNotExist());
    }
}
