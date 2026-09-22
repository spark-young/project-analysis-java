package com.spark.projectanalysis.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.projectanalysis.config.CallgraphPaths;
import com.spark.projectanalysis.service.ProjectRegistry;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目注册表 REST 接口：运行时状态检测（MISSING / jar 型 / NEEDS_COMPILE / NEEDS_ANALYZE）
 * 与本地注册的路径归一化。
 *
 * <p>用真实 Spring 上下文（与 {@code AnalysisControllerTest} 同风格），注册表写入被隔离到
 * 本类专属的临时 {@code callgraph.home}，避免污染真实 {@code D:\.callgraph}。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectsControllerTest {

    @TempDir
    static Path tempHome;

    @Autowired
    MockMvc mvc;

    @Autowired
    ProjectRegistry registry;

    @TempDir
    Path temp;

    private final ObjectMapper json = new ObjectMapper();

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

    /** 直接把一条记录写进注册表（模拟已导入的项目），返回其 id。 */
    private String register(String name, String type, Path projectPath) {
        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = name;
        p.type = type;
        p.projectPath = projectPath.toString();
        p.lastOpenedAt = System.currentTimeMillis();
        p.createdAt = p.lastOpenedAt;
        registry.save(p);
        return p.id;
    }

    /** 取 /api/projects 响应中指定 id 的项目（含状态检测结果）。 */
    private ProjectRegistry.RegisteredProject projectById(String id) throws Exception {
        MvcResult res = mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andReturn();
        List<?> items = json.readValue(res.getResponse().getContentAsByteArray(), List.class);
        for (Object o : items) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
            if (id.equals(m.get("id"))) {
                return json.convertValue(m, ProjectRegistry.RegisteredProject.class);
            }
        }
        fail("未在 /api/projects 响应中找到项目: " + id);
        return null; // unreachable
    }

    // ------------------------------------------------------------------
    // 项目状态检测
    // ------------------------------------------------------------------

    @Test
    void test_list_statusMissing_whenPathNotOnDisk() throws Exception {
        String id = register("gone", "LOCAL", temp.resolve("no-such-project-dir"));

        ProjectRegistry.RegisteredProject p = projectById(id);

        assertEquals("MISSING", p.changeStatus);
        assertEquals(false, p.existsOnDisk);
        assertEquals(false, p.compiled);
    }

    @Test
    void test_list_jarProject_validOnDiskAndCompiled() throws Exception {
        // 状态检测只判断“是不是文件”，不解析 jar 内容，故任意字节即可
        Path jar = temp.resolve("app.jar");
        Files.write(jar, new byte[]{0x50, 0x4B, 0x03, 0x04});
        String id = register("fatjar", "LOCAL", jar);

        ProjectRegistry.RegisteredProject p = projectById(id);

        assertEquals(true, p.existsOnDisk, "jar 文件应被视为有效项目");
        assertEquals(true, p.compiled, "jar 本身即编译产物");
        assertEquals(false, p.analyzed);
        assertEquals("NEEDS_ANALYZE", p.changeStatus);
    }

    @Test
    void test_list_directoryWithoutArtifacts_needsCompile() throws Exception {
        Path project = Files.createDirectories(temp.resolve("plain"));
        String id = register("plain", "LOCAL", project);

        ProjectRegistry.RegisteredProject p = projectById(id);

        assertEquals(true, p.existsOnDisk);
        assertEquals(false, p.compiled);
        assertEquals("NEEDS_COMPILE", p.changeStatus);
    }

    @Test
    void test_list_directoryWithClasses_noCache_needsAnalyze() throws Exception {
        Path project = Files.createDirectories(temp.resolve("compiled"));
        Path classes = Files.createDirectories(project.resolve("target/classes/com/demo"));
        Files.write(classes.resolve("A.class"), new byte[]{(byte) 0xCA, (byte) 0xFE});
        String id = register("compiled", "LOCAL", project);

        ProjectRegistry.RegisteredProject p = projectById(id);

        assertEquals(true, p.compiled, "存在 target/classes 且有 .class 应判定为已编译");
        assertEquals(false, p.analyzed);
        assertEquals("NEEDS_ANALYZE", p.changeStatus);
    }

    // ------------------------------------------------------------------
    // 路径归一化
    // ------------------------------------------------------------------

    @Test
    void test_registerLocal_normalizesBuildOutputDirToProjectRoot() throws Exception {
        Path project = Files.createDirectories(temp.resolve("gradleapp"));
        Files.write(project.resolve("build.gradle"), new byte[0]);
        Files.createDirectories(project.resolve("build/classes/java/main/com/demo"));
        Files.write(project.resolve("build/classes/java/main/com/demo/A.class"), new byte[]{1});
        // 用户填的是构建产物目录 build/，应归一化到工程根
        Path buildDir = project.resolve("build");

        String body = json.writeValueAsString(java.util.Map.of("path", buildDir.toString()));

        mvc.perform(post("/api/projects/local").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LOCAL"))
                .andExpect(jsonPath("$.projectPath").value(project.toString()));
    }

    @Test
    void test_registerLocal_rootPath_keptUnchanged() throws Exception {
        Path project = Files.createDirectories(temp.resolve("rootapp"));
        Files.write(project.resolve("build.gradle"), new byte[0]);
        Files.createDirectories(project.resolve("build/classes/java/main/com/demo"));

        String body = json.writeValueAsString(java.util.Map.of("path", project.toString()));

        mvc.perform(post("/api/projects/local").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectPath").value(project.toString()));
    }

    @Test
    void test_registerLocal_emptyPath_badRequest() throws Exception {
        mvc.perform(post("/api/projects/local").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ------------------------------------------------------------------
    // 缓存接口 JSON 形状契约锁（OPT-28 切片4：Map → DTO 化）
    // ------------------------------------------------------------------

    @Test
    void test_saveSingleCache_okShape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("sscache"));
        String id = register("sscache", "LOCAL", project);
        mvc.perform(post("/api/projects/" + id + "/cache/save-single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":{\"foo\":\"bar\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void test_loadSingleCache_noCache_shape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("lscache-empty"));
        String id = register("lscache-empty", "LOCAL", project);
        // 形状锁：无缓存时只有 currentEntryCount + hasCache 两个字段
        MvcResult r = mvc.perform(get("/api/projects/" + id + "/cache/load-single"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEntryCount").value(0))
                .andExpect(jsonPath("$.hasCache").value(false))
                .andExpect(jsonPath("$.dirty").doesNotExist())
                .andExpect(jsonPath("$.cachedEntryCount").doesNotExist())
                .andExpect(jsonPath("$.analyzedAt").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn();
        String json = r.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                json.matches("\\{\"currentEntryCount\":0,\"hasCache\":false\\}"),
                "响应形状应为 {\"currentEntryCount\":0,\"hasCache\":false}: " + json);
    }

    @Test
    void test_loadSingleCache_withCache_shape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("lscache-full"));
        String id = register("lscache-full", "LOCAL", project);
        mvc.perform(post("/api/projects/" + id + "/cache/save-single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":{\"foo\":\"bar\"}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/projects/" + id + "/cache/load-single"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCache").value(true))
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.cachedEntryCount").value(0))
                .andExpect(jsonPath("$.currentEntryCount").value(0))
                .andExpect(jsonPath("$.analyzedAt").isNumber())
                .andExpect(jsonPath("$.result.foo").value("bar"));
    }

    @Test
    void test_loadCacheFile_miss_shape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("lcfile-miss"));
        String id = register("lcfile-miss", "LOCAL", project);
        mvc.perform(get("/api/projects/" + id + "/cache/load-file").param("file", "nope.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("缓存文件不存在或已过期: nope.json"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void test_loadCacheFile_hit_shape() throws Exception {
        Path project = Files.createDirectories(temp.resolve("lcfile-hit"));
        String id = register("lcfile-hit", "LOCAL", project);
        // 直接往项目缓存目录（<项目>/.callgraph/cache）种一个最小 AnalysisResult JSON
        Path cacheDir = project.resolve(".callgraph").resolve("cache");
        Files.createDirectories(cacheDir);
        Files.write(cacheDir.resolve("t.json"),
                "{\"className\":\"com.demo.Foo\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/projects/" + id + "/cache/load-file").param("file", "t.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.className").value("com.demo.Foo"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
