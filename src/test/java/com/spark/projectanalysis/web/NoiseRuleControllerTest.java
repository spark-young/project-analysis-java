package com.spark.projectanalysis.web;

import com.spark.projectanalysis.config.CallgraphPaths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NoiseRuleController 项目级配置详情的 JSON 形状契约锁
 * （OPT-28 切片5：Map → NoiseProjectDetail DTO 化）。
 *
 * <p>前端 app.js:2922-2923 消费 detail.globalOverrides / detail.customRules；
 * app.js:3327-3328 消费 PUT 响应的同名字段 —— 字段名必须逐字不变。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NoiseRuleControllerTest {

    @TempDir
    static Path tempHome;

    @TempDir
    Path temp;

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void isolateHome() throws Exception {
        System.setProperty("callgraph.home", tempHome.toString());
        resetCachedHome();
    }

    @AfterAll
    static void restoreHome() throws Exception {
        System.clearProperty("callgraph.home");
        resetCachedHome();
    }

    private static void resetCachedHome() throws Exception {
        Field cachedHome = CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }

    @Test
    void test_projectDetail_jsonShapeLocked() throws Exception {
        Path project = Files.createDirectories(temp.resolve("noiseproj"));
        mvc.perform(get("/api/noise-rules/project-detail")
                        .param("projectPath", project.toString()))
                .andExpect(status().isOk())
                // 三个键恒出现（原 Map 版本全部 put 非 null 值）
                .andExpect(jsonPath("$.globalRules").isArray())
                .andExpect(jsonPath("$.globalOverrides").isMap())
                .andExpect(jsonPath("$.customRules").isArray());
    }

    @Test
    void test_saveProjectDetail_roundTrip_shape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("noiseproj2"));
        String body = "{\"globalOverrides\":{\"r1\":false},\"customRules\":[]}";
        // PUT /api/noise-rules?projectPath=... 返回保存后的详情（同 DTO），前端读 globalOverrides/customRules
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/noise-rules").param("projectPath", project.toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalRules").isArray())
                .andExpect(jsonPath("$.globalOverrides.r1").value(false))
                .andExpect(jsonPath("$.customRules").isArray());
    }
}
