package com.spark.callgraph.service;

import com.spark.callgraph.config.CallgraphPaths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ProjectRegistry 扩展字段向后兼容性测试 */
class ProjectRegistryTest {

    @TempDir
    static Path tempHome;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("callgraph.home", tempHome.toString());
        Field cachedHome = CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }

    @AfterAll
    static void cleanup() throws Exception {
        System.clearProperty("callgraph.home");
        Field cachedHome = CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }

    /** 旧 JSON（无新字段）反序列化不报错且新字段为 null */
    @Test
    void test_backwardCompat_oldJson() throws Exception {
        String oldJson = "[{"
                + "\"id\":\"old-1\","
                + "\"name\":\"legacy\","
                + "\"type\":\"GIT\","
                + "\"projectPath\":\"/tmp/legacy\","
                + "\"gitUrl\":\"https://github.com/user/repo.git\","
                + "\"gitBranch\":\"main\","
                + "\"lastOpenedAt\":1700000000000,"
                + "\"createdAt\":1700000000000"
                + "}]";
        Files.createDirectories(tempHome);
        Files.write(tempHome.resolve("projects.json"), oldJson.getBytes());

        ProjectRegistry registry = new ProjectRegistry();
        List<ProjectRegistry.RegisteredProject> list = registry.list();
        assertEquals(1, list.size());
        ProjectRegistry.RegisteredProject p = list.get(0);

        assertEquals("old-1", p.id);
        assertEquals("main", p.gitBranch);

        assertNull(p.gitToken, "旧 JSON 无 gitToken 应反序列化为 null");
        assertNull(p.gitUsername, "旧 JSON 无 gitUsername 应反序列化为 null");
        assertNull(p.currentRef, "旧 JSON 无 currentRef 应反序列化为 null");
        assertNull(p.currentRefType, "旧 JSON 无 currentRefType 应反序列化为 null");
        assertNull(p.remoteUpdateStatus, "旧 JSON 无 remoteUpdateStatus 应反序列化为 null");
        assertNull(p.lastCheckTime, "旧 JSON 无 lastCheckTime 应反序列化为 null");
    }

    /** 新字段写入后再读取，值正确保留 */
    @Test
    void test_saveAndRead_newFields() throws Exception {
        ProjectRegistry registry = new ProjectRegistry();

        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = "new-1";
        p.name = "with-fields";
        p.type = "GIT";
        p.projectPath = tempHome.resolve("workdir").toString();
        p.gitUrl = "https://github.com/user/repo.git";
        p.gitBranch = "main";
        p.lastOpenedAt = System.currentTimeMillis();
        p.createdAt = System.currentTimeMillis();

        // 设置新字段
        p.gitToken = "ghp_test123";
        p.gitUsername = "myuser";
        p.currentRef = "develop";
        p.currentRefType = "BRANCH";
        p.remoteUpdateStatus = "UP_TO_DATE";
        p.lastCheckTime = System.currentTimeMillis();

        registry.save(p);

        // 重新读取
        ProjectRegistry.RegisteredProject loaded = registry.get("new-1");
        assertNotNull(loaded);
        assertEquals("ghp_test123", loaded.gitToken);
        assertEquals("myuser", loaded.gitUsername);
        assertEquals("develop", loaded.currentRef);
        assertEquals("BRANCH", loaded.currentRefType);
        assertEquals("UP_TO_DATE", loaded.remoteUpdateStatus);
        assertNotNull(loaded.lastCheckTime);
    }

    /** LOCAL 类型项目不受新字段影响 */
    @Test
    void test_localProject_noGitFields() throws Exception {
        ProjectRegistry registry = new ProjectRegistry();

        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = "local-1";
        p.name = "local-only";
        p.type = "LOCAL";
        p.projectPath = "C:/projects/myapp";
        p.lastOpenedAt = System.currentTimeMillis();
        p.createdAt = System.currentTimeMillis();

        registry.save(p);

        ProjectRegistry.RegisteredProject loaded = registry.get("local-1");
        assertNotNull(loaded);
        assertEquals("LOCAL", loaded.type);
        assertNull(loaded.gitUrl);
        assertNull(loaded.currentRef);
        assertNull(loaded.remoteUpdateStatus);
    }
}