package com.spark.projectanalysis.web;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EntryListController 的 JSON 形状契约锁（OPT-28 切片2：Map → DTO 化）。
 *
 * <p>所有断言只锁「字段名 + 出现条件 + 类型」，与改前 Map 版本逐字一致；
 * 列表内容断言不依赖顺序。注册表/entries.json 持久化被隔离到本类专属的
 * 临时 {@code callgraph.home}；写状态接口各自使用独立的项目目录，
 * 避免 entries.json（按 projectPath 寻址）跨用例串扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntryListControllerTest {

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
        Field cachedHome = com.spark.projectanalysis.config.CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }

    /** 注册一个项目记录，返回其 id（不落真实注册表：callgraph.home 已隔离）。 */
    private String registerProject(Path projectPath) {
        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = UUID.randomUUID().toString();
        p.name = "entrylist-" + p.id.substring(0, 8);
        p.type = "LOCAL";
        p.projectPath = projectPath.toString();
        p.lastOpenedAt = System.currentTimeMillis();
        p.createdAt = p.lastOpenedAt;
        registry.save(p);
        return p.id;
    }

    /** 每个写状态的用例独占一个项目目录（entries.json 按 projectPath 寻址）。 */
    private Path freshProjectDir(String tag) throws Exception {
        return Files.createDirectories(temp.resolve(tag + "-" + UUID.randomUUID()));
    }

    private static String itemJson(String className, String methodName) {
        return "{\"className\":\"" + className + "\",\"methodName\":\"" + methodName
                + "\",\"descriptor\":\"()V\",\"source\":\"MANUAL\",\"group\":\"MANUAL\"}";
    }

    // ------------------------------------------------------------------
    // scan / scan-manual（只读接口，共用 demo 编译产物目录）
    // ------------------------------------------------------------------

    @Test
    void test_scan_jsonShapeLocked() throws Exception {
        String id = registerProject(demoClasses);
        mvc.perform(post("/api/projects/" + id + "/entries/scan")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.existed").isNumber())
                .andExpect(jsonPath("$.scanTotalGroups").isNumber())
                .andExpect(jsonPath("$.scanTotalEntries").isNumber())
                .andExpect(jsonPath("$.projectPath").value(demoClasses.toString()));
    }

    @Test
    void test_scanManual_jsonShapeLocked() throws Exception {
        String id = registerProject(demoClasses);
        mvc.perform(post("/api/projects/" + id + "/entries/scan-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"com.demo.OrderService\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.existed").isNumber())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.projectPath").value(demoClasses.toString()));
    }

    // ------------------------------------------------------------------
    // 手动添加：类名 / 包名 → 枚举方法（不套用扫描策略）
    // ------------------------------------------------------------------

    @Test
    void test_scanManual_class_listsAllMethods_excludingCtorAndSynthetic() throws Exception {
        String id = registerProject(demoClasses);
        mvc.perform(post("/api/projects/" + id + "/entries/scan-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"com.demo.OrderService\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CLASS"))
                .andExpect(jsonPath("$.resolvedName").value("com.demo.OrderService"))
                .andExpect(jsonPath("$.candidates[*].methodName", hasItem("place")))
                // 构造器(<init>/<clinit>)与编译器合成方法(lambda$/access$)不作为候选
                .andExpect(jsonPath("$.candidates[*].methodName", everyItem(not(startsWith("<")))))
                .andExpect(jsonPath("$.candidates[*].methodName", everyItem(not(containsString("$")))));
    }

    @Test
    void test_scanManual_package_listsOnlyThisLevel_notSubPackages() throws Exception {
        String id = registerProject(demoClasses);
        mvc.perform(post("/api/projects/" + id + "/entries/scan-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"com.demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PACKAGE"))
                .andExpect(jsonPath("$.resolvedName").value("com.demo"))
                .andExpect(jsonPath("$.candidates[*].methodName", hasItem("place")));
        // 父包 com 本层没有类：若递归子包就会返回 com.demo 的全部方法，这里必须为 0
        mvc.perform(post("/api/projects/" + id + "/entries/scan-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PACKAGE"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void test_scanManual_unknownQuery_reportsNone() throws Exception {
        String id = registerProject(demoClasses);
        mvc.perform(post("/api/projects/" + id + "/entries/scan-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"com.no.such.pkg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NONE"))
                .andExpect(jsonPath("$.total").value(0));
    }

    // ------------------------------------------------------------------
    // merge / add / add-batch
    // ------------------------------------------------------------------

    @Test
    void test_merge_addedThenExisted() throws Exception {
        String id = registerProject(freshProjectDir("merge"));
        String body = "[" + itemJson("com.demo.MergeA", "run") + "," + itemJson("com.demo.MergeB", "run") + "]";
        // 第一次：全部新增
        mvc.perform(post("/api/projects/" + id + "/entries/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.existed").value(0));
        // 第二次：全部已存在
        mvc.perform(post("/api/projects/" + id + "/entries/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.existed").value(2));
    }

    @Test
    void test_add_jsonShapeLocked() throws Exception {
        String id = registerProject(freshProjectDir("add"));
        String body = "{\"className\":\"com.demo.AddOne\",\"methodName\":\"exec\",\"descriptor\":\"()V\"}";
        mvc.perform(post("/api/projects/" + id + "/entries/add")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(true))
                .andExpect(jsonPath("$.list.version").value(1))
                .andExpect(jsonPath("$.list.confirmed[0].className").value("com.demo.AddOne"))
                .andExpect(jsonPath("$.list.confirmed[0].methodName").value("exec"))
                .andExpect(jsonPath("$.list.confirmed[0].source").value("MANUAL"));
        // 重复添加 → added=false（字段仍存在，值为 false）
        mvc.perform(post("/api/projects/" + id + "/entries/add")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(false));
    }

    @Test
    void test_addBatch_jsonShapeLocked() throws Exception {
        String id = registerProject(freshProjectDir("addbatch"));
        String body = "[" + itemJson("com.demo.BatchA", "go") + "," + itemJson("com.demo.BatchB", "go") + "]";
        mvc.perform(post("/api/projects/" + id + "/entries/add-batch")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.existed").value(0))
                .andExpect(jsonPath("$.list.confirmed.length()").value(2));
        // 重复提交 → added=0, existed=2
        mvc.perform(post("/api/projects/" + id + "/entries/add-batch")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.existed").value(2));
    }

    // ------------------------------------------------------------------
    // exclude / exclude-batch / restore / delete
    // ------------------------------------------------------------------

    @Test
    void test_excludeAndRestore_jsonShapeLocked() throws Exception {
        String id = registerProject(freshProjectDir("exrest"));
        String key = "com.demo.ExTgt#stop#()V";
        String body = "{\"className\":\"com.demo.ExTgt\",\"methodName\":\"stop\",\"descriptor\":\"()V\"}";
        mvc.perform(post("/api/projects/" + id + "/entries/add")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // 不存在的 key → ok=false（false 也必须出现在 JSON 中，而非字段缺失）
        mvc.perform(post("/api/projects/" + id + "/entries/exclude")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"com.demo.NoSuch#m#()V\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false));
        // 存在的 key → ok=true
        mvc.perform(post("/api/projects/" + id + "/entries/exclude")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // 恢复：excluded → confirmed
        mvc.perform(post("/api/projects/" + id + "/entries/restore")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        // 再恢复一次 → ok=false
        mvc.perform(post("/api/projects/" + id + "/entries/restore")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void test_excludeBatch_jsonShapeLocked() throws Exception {
        String id = registerProject(freshProjectDir("exbatch"));
        String body = "[" + itemJson("com.demo.Bx1", "m") + "," + itemJson("com.demo.Bx2", "m") + "]";
        mvc.perform(post("/api/projects/" + id + "/entries/add-batch")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        String payload = "[{\"key\":\"com.demo.Bx1#m#()V\",\"reason\":\"测试\"},"
                + "{\"key\":\"com.demo.Bx2#m#()V\",\"reason\":\"测试\"},"
                + "{\"key\":\"com.demo.Missing#m#()V\",\"reason\":\"测试\"}]";
        mvc.perform(post("/api/projects/" + id + "/entries/exclude/batch")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluded").value(2));
    }

    @Test
    void test_delete_jsonShapeLocked() throws Exception {
        String id = registerProject(freshProjectDir("delete"));
        String body = "{\"className\":\"com.demo.DelTgt\",\"methodName\":\"kill\",\"descriptor\":\"()V\"}";
        mvc.perform(post("/api/projects/" + id + "/entries/add")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/projects/" + id + "/entries").param("key", "com.demo.DelTgt#kill#()V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(1));
        // 再删一次 → removed=0（0 也必须出现在 JSON 中）
        MvcResult result = mvc.perform(delete("/api/projects/" + id + "/entries")
                        .param("key", "com.demo.DelTgt#kill#()V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(0))
                .andReturn();
        // 形状锁：响应体只应有 removed 一个字段（Map 版本同样只有一个键）
        String json = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                json.matches("\\{\"removed\":0\\}"), "响应形状应为 {\"removed\":0}: " + json);
    }

    @Test
    void test_unknownProject_scan_returnsError() throws Exception {
        mvc.perform(post("/api/projects/no-such-id/entries/scan")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    // GET /entries 本就是 EntryList DTO（非 Map），此用例确保其形状未被波及
    @Test
    void test_getEntries_stillEntryListDto() throws Exception {
        String id = registerProject(freshProjectDir("get"));
        mvc.perform(get("/api/projects/" + id + "/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.confirmed").isArray())
                .andExpect(jsonPath("$.excluded").isArray());
    }
}
