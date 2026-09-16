package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.ProjectRegistry.RegisteredProject;
import com.spark.projectanalysis.service.dto.GitRefs;
import com.spark.projectanalysis.service.dto.RemoteStatus;
import com.spark.projectanalysis.service.dto.SwitchRequest;
import com.spark.projectanalysis.service.dto.SwitchStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitRefService 服务层测试：分支/Tag 列出、远端更新检测、异步切换（含状态机）。
 * <p>
 * 与 GitCloneServiceRefTest / GitPrepareServiceTest 复用相同的夹具模式：
 * 本地 bare 仓库模拟远端，纯 JUnit 5（无 Spring），用桩替代编译以避免实际 Maven/javac 执行。
 */
class GitRefServiceTest {

    @TempDir
    Path temp;

    // ------------------------------------------------------------------
    // Fixture：文件级与 Git 操作
    // ------------------------------------------------------------------

    /** 创建包含单文件 Demo.java 的工作仓库（无 pom.xml，让切换后走 javac 重编译分支） */
    private Path initWorkRepo() throws Exception {
        Path work = Files.createDirectories(temp.resolve("src"));
        Path javaDir = work.resolve("src/main/java/com/git/demo");
        Files.createDirectories(javaDir);
        Files.write(javaDir.resolve("Demo.java"),
                "package com.git.demo; public class Demo { }".getBytes());
        try (Git git = Git.init().setDirectory(work.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init")
                    .setAuthor("t", "t@t.com").setCommitter("t", "t@t.com").call();
        }
        return work;
    }

    /** 初始化 bare 远端并 push（含 tags） */
    private Path pushToBare(Path work) throws Exception {
        Path bare = temp.resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().setPushTags().call();
        }
        return bare;
    }

    /** 克隆 bare 仓库到工作目录（模拟 GitCloneService 的新拉取） */
    private Path cloneBare(Path bare) throws Exception {
        Path target = temp.resolve("cloned");
        new GitCloneService().clone(bare.toUri().toString(), null, null, null, target);
        return target;
    }

    // ------------------------------------------------------------------
    // Stubs
    // ------------------------------------------------------------------

    static class StubMaven extends MavenCompileService {
        Path dir;
        boolean fail;

        @Override
        public CompileResult compile(Path projectDir) {
            this.dir = projectDir;
            return fail ? CompileResult.failure("模拟 Maven 编译失败") : CompileResult.success();
        }
    }

    static class StubJavac extends JavacCompileService {
        Path src;
        boolean fail;

        @Override
        public CompileResult compile(Path srcRoot, Path buildRoot) {
            this.src = srcRoot;
            return fail ? CompileResult.failure("模拟 javac 编译失败") : CompileResult.success();
        }
    }

    /** 内存注册表：避免读写磁盘 projects.json，不依赖 CallgraphPaths */
    static class InMemoryRegistry extends ProjectRegistry {
        final Map<String, RegisteredProject> store = new ConcurrentHashMap<>();

        @Override
        public RegisteredProject get(String id) {
            return store.get(id);
        }

        @Override
        public synchronized RegisteredProject save(RegisteredProject p) {
            store.put(p.id, p);
            return p;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RegisteredProject registerGit(InMemoryRegistry reg, Path workdir, String url) {
        RegisteredProject p = new RegisteredProject();
        p.id = "proj-1";
        p.name = "demo";
        p.type = "GIT";
        p.projectPath = workdir.toString();
        p.gitUrl = url;
        long now = System.currentTimeMillis();
        p.createdAt = now;
        p.lastOpenedAt = now;
        reg.save(p);
        return p;
    }

    private static SwitchRequest req(String ref, String type) {
        SwitchRequest r = new SwitchRequest();
        r.setRef(ref);
        r.setRefType(type);
        return r;
    }

    /** 轮询等待任务进入终态（DONE / FAILED），最多 15 秒 */
    private static SwitchStatus awaitTerminal(GitRefService svc, String jobId) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            SwitchStatus s = svc.switchStatus(jobId);
            if ("DONE".equals(s.getStatus()) || "FAILED".equals(s.getStatus())) return s;
            Thread.sleep(100);
        }
        throw new AssertionError("任务未在 15 秒内进入终态: "
                + svc.switchStatus(jobId).getStatus());
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void test_listRefs_returnsBranchesAndTags() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("feature-x").call();
            git.tag().setName("v1.0").call();
        }
        Path bare = pushToBare(work);
        Path cloned = cloneBare(bare);
        InMemoryRegistry reg = new InMemoryRegistry();
        registerGit(reg, cloned, bare.toUri().toString());
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        GitRefs refs = svc.listRefs("proj-1");

        assertTrue(refs.getBranches().contains("feature-x"), "应包含 feature-x 分支");
        assertTrue(refs.getTags().contains("v1.0"), "应包含 v1.0 标签");
        assertNotNull(refs.getDefaultBranch(), "应有默认分支");
        assertTrue(refs.getBranches().contains(refs.getDefaultBranch()));
    }

    @Test
    void test_listRefs_localProject_rejected() {
        InMemoryRegistry reg = new InMemoryRegistry();
        RegisteredProject p = new RegisteredProject();
        p.id = "local-1";
        p.type = "LOCAL";
        p.projectPath = temp.toString();
        long now = System.currentTimeMillis();
        p.createdAt = now;
        p.lastOpenedAt = now;
        reg.save(p);
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        AnalysisException ex = assertThrows(AnalysisException.class,
                () -> svc.listRefs("local-1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void test_listRefs_missingProject_notFound() {
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), new InMemoryRegistry());

        AnalysisException ex = assertThrows(AnalysisException.class,
                () -> svc.listRefs("no-such"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void test_checkRemoteUpdate_upToDate_updatesRegistry() throws Exception {
        Path work = initWorkRepo();
        Path bare = pushToBare(work);
        Path cloned = cloneBare(bare);
        InMemoryRegistry reg = new InMemoryRegistry();
        registerGit(reg, cloned, bare.toUri().toString());
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        RemoteStatus rs = svc.checkRemoteUpdate("proj-1");

        assertEquals("UP_TO_DATE", rs.getStatus());
        assertTrue(rs.getCheckedAt() > 0, "应有检测时间戳");

        RegisteredProject saved = reg.get("proj-1");
        assertEquals("UP_TO_DATE", saved.remoteUpdateStatus);
        assertNotNull(saved.lastCheckTime);
        assertTrue(saved.lastCheckTime > 0, "注册表应记录检测时间");
    }

    @Test
    void test_startSwitch_toBranch_pollsToDone() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("dev").call();
        }
        Path bare = pushToBare(work);
        Path cloned = cloneBare(bare);
        InMemoryRegistry reg = new InMemoryRegistry();
        registerGit(reg, cloned, bare.toUri().toString());
        StubJavac javac = new StubJavac();
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), javac, reg);

        SwitchStatus s = svc.startSwitch("proj-1", req("dev", "BRANCH"));

        assertNotNull(s.getJobId());
        SwitchStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("DONE", done.getStatus(), done.getMessage());
        assertEquals(100, done.getProgress());

        // 注册表应反映切换后的引用
        RegisteredProject saved = reg.get("proj-1");
        assertEquals("dev", saved.currentRef);
        assertEquals("BRANCH", saved.currentRefType);

        // 实际工作目录应已切换到目标分支
        try (Git git = Git.open(cloned.toFile())) {
            assertEquals("dev", git.getRepository().getBranch());
        }

        // 无 pom.xml 的工程切换后应走 javac 编译分支
        assertNotNull(javac.src, "无 pom.xml 应走 javac 编译");
    }

    @Test
    void test_startSwitch_toTag_pollsToDone() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.tag().setName("v1").call();
        }
        Path bare = pushToBare(work);
        Path cloned = cloneBare(bare);
        InMemoryRegistry reg = new InMemoryRegistry();
        registerGit(reg, cloned, bare.toUri().toString());
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        SwitchStatus s = svc.startSwitch("proj-1", req("v1", "TAG"));

        SwitchStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("DONE", done.getStatus(), done.getMessage());

        RegisteredProject saved = reg.get("proj-1");
        assertEquals("v1", saved.currentRef);
        assertEquals("TAG", saved.currentRefType);
    }

    @Test
    void test_startSwitch_invalidRef_fails() throws Exception {
        Path work = initWorkRepo();
        Path bare = pushToBare(work);
        Path cloned = cloneBare(bare);
        InMemoryRegistry reg = new InMemoryRegistry();
        registerGit(reg, cloned, bare.toUri().toString());
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        SwitchStatus s = svc.startSwitch("proj-1", req("no-such-branch", "BRANCH"));

        SwitchStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("FAILED", done.getStatus());
        assertNotNull(done.getMessage());
        assertFalse(done.getMessage().isEmpty());
    }

    @Test
    void test_startSwitch_emptyRef_rejected() {
        InMemoryRegistry reg = new InMemoryRegistry();
        RegisteredProject p = new RegisteredProject();
        p.id = "proj-1";
        p.type = "GIT";
        p.projectPath = temp.toString();
        p.gitUrl = "file:///dummy.git";
        long now = System.currentTimeMillis();
        p.createdAt = now;
        p.lastOpenedAt = now;
        reg.save(p);
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), reg);

        assertThrows(AnalysisException.class, () -> svc.startSwitch("proj-1", req("", "BRANCH")));
    }

    @Test
    void test_switchStatus_unknownJob_notFound() {
        GitRefService svc = new GitRefService(new GitCloneService(),
                new StubMaven(), new StubJavac(), new InMemoryRegistry());

        AnalysisException ex = assertThrows(AnalysisException.class,
                () -> svc.switchStatus("no-such-job"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}