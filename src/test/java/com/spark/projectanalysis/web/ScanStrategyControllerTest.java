package com.spark.projectanalysis.web;

import com.spark.projectanalysis.config.CallgraphPaths;
import com.spark.projectanalysis.service.ProjectRegistry;
import com.spark.projectanalysis.testsupport.Fixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScanStrategyController 的 JSON 形状契约锁（OPT-28 切片3：Map → DTO 化）。
 *
 * <p>断言只锁「字段名 + 类型」，与改前 Map 版本逐字一致。
 * 策略持久化被隔离到本类专属的临时 {@code callgraph.home}。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScanStrategyControllerTest {

    static Path demoClasses;

    @TempDir
    static Path tempHome;

    @TempDir
    Path temp;

    @Autowired
    MockMvc mvc;

    @Autowired
    ProjectRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("callgraph.home", tempHome.toString());
        resetCachedHome();
        Path libJar = Fixtures.compileToJar("lib");
        demoClasses = Fixtures.compileToDir("demo", libJar);
    }

    @AfterAll
    static void tearDown() throws Exception {
        System.clearProperty("callgraph.home");
        resetCachedHome();
    }

    private static void resetCachedHome() throws Exception {
        Field cachedHome = CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }

    private String registerDemoProject() {
        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = "scanstrat-" + p.id.substring(0, 8);
        p.type = "LOCAL";
        p.projectPath = demoClasses.toString();
        p.lastOpenedAt = System.currentTimeMillis();
        p.createdAt = p.lastOpenedAt;
        registry.save(p);
        return p.id;
    }

    // ------------------------------------------------------------------
    // 全局策略
    // ------------------------------------------------------------------

    @Test
    void test_saveAndResetGlobal_okShape() throws Exception {
        // 形状锁：PUT /global 与 POST /global/reset 响应只有 ok=true 一个字段
        mvc.perform(put("/api/scan-strategy/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profiles\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        MvcResult r = mvc.perform(post("/api/scan-strategy/global/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.matches("\\{\"ok\":true\\}"),
                "响应形状应为 {\"ok\":true}: " + json);
    }

    @Test
    void test_getGlobal_returnsStrategyDto() throws Exception {
        mvc.perform(get("/api/scan-strategy/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles").isArray());
    }

    // ------------------------------------------------------------------
    // 项目级策略
    // ------------------------------------------------------------------

    @Test
    void test_saveAndResetProjectStrategy_okShape() throws Exception {
        String id = registerDemoProject();
        mvc.perform(put("/api/scan-strategy/project/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profiles\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/api/scan-strategy/project/" + id + "/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        // 项目级 GET（全局+项目合并）返回 ScanStrategy DTO
        mvc.perform(get("/api/scan-strategy/project/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles").isArray());
    }

    // ------------------------------------------------------------------
    // 新建规则 / 方案
    // ------------------------------------------------------------------

    @Test
    void test_newRule_jsonShapeLocked() throws Exception {
        MvcResult r = mvc.perform(post("/api/scan-strategy/rule/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").isString())
                .andReturn();
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.matches("\\{\"ruleId\":\"[^\"]+\"\\}"),
                "响应形状应为 {\"ruleId\":\"...\"}: " + json);
    }

    @Test
    void test_newProfile_jsonShapeLocked() throws Exception {
        // 前端 app.js:4298 消费 data.profileId —— 契约锁重点字段
        MvcResult r = mvc.perform(post("/api/scan-strategy/profile/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").isString())
                .andReturn();
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.matches("\\{\"profileId\":\"[^\"]+\"\\}"),
                "响应形状应为 {\"profileId\":\"...\"}: " + json);
    }

    // ------------------------------------------------------------------
    // 策略驱动扫描
    // ------------------------------------------------------------------

    @Test
    void test_scanWithStrategy_jsonShapeLocked() throws Exception {
        String id = registerDemoProject();
        mvc.perform(post("/api/scan-strategy/scan/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.existed").isNumber())
                .andExpect(jsonPath("$.scanTotalGroups").isNumber())
                .andExpect(jsonPath("$.scanTotalEntries").isNumber())
                .andExpect(jsonPath("$.projectPath").value(demoClasses.toString()))
                .andExpect(jsonPath("$.profileName").isString());
    }
}
