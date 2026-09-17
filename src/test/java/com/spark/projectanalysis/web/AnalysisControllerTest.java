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
}
