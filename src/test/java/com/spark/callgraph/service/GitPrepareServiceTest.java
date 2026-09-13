package com.spark.callgraph.service;

import com.spark.callgraph.service.dto.GitPrepareRequest;
import com.spark.callgraph.service.dto.GitPrepareStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Git 准备任务（克隆→编译）异步编排。编译步骤用桩替换，克隆走真实 JGit 本地仓库。 */
class GitPrepareServiceTest {

    @TempDir
    Path temp;

    /** 编译桩：记录收到的目录，可模拟失败 */
    static class StubCompileService extends MavenCompileService {
        Path compiledDir;
        boolean fail;

        @Override
        public CompileResult compile(Path projectDir) {
            this.compiledDir = projectDir;
            return fail ? CompileResult.failure("模拟编译失败") : CompileResult.success();
        }
    }

    /** javac 编译桩：可记录收到的源码目录，可模拟成功/失败 */
    static class StubJavacService extends JavacCompileService {
        Path srcDir;
        boolean fail;

        @Override
        public CompileResult compile(Path srcRoot, Path buildRoot) {
            this.srcDir = srcRoot;
            try {
                java.nio.file.Files.createDirectories(buildRoot.resolve("classes"));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            return fail ? CompileResult.failure("模拟 javac 编译失败") : CompileResult.success();
        }
    }

    private Path remoteWithProject() throws Exception {
        Path work = Files.createDirectories(temp.resolve("src"));
        Files.write(work.resolve("pom.xml"), "<project/>".getBytes());
        try (Git git = Git.init().setDirectory(work.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init")
                    .setAuthor("t", "t@t.com").setCommitter("t", "t@t.com").call();
        }
        Path bare = temp.resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();
        }
        return bare;
    }

    private GitPrepareRequest request(String url) {
        GitPrepareRequest req = new GitPrepareRequest();
        req.setRepoUrl(url);
        return req;
    }

    private GitPrepareService service(StubCompileService stub) {
        return service(stub, new StubJavacService());
    }

    private GitPrepareService service(StubCompileService stub, StubJavacService javac) {
        return new GitPrepareService(new GitCloneService(), stub, javac, temp.toString());
    }

    private GitPrepareStatus awaitTerminal(GitPrepareService svc, String jobId) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            GitPrepareStatus s = svc.status(jobId);
            if ("DONE".equals(s.getStatus()) || "FAILED".equals(s.getStatus())) return s;
            Thread.sleep(100);
        }
        throw new AssertionError("任务未在 15 秒内进入终态");
    }

    @Test
    void test_prepare_cloneCompileDone() throws Exception {
        Path bare = remoteWithProject();
        StubCompileService stub = new StubCompileService();
        GitPrepareService svc = service(stub);

        GitPrepareStatus s = svc.prepare(request(bare.toUri().toString()));

        assertNotNull(s.getJobId());
        GitPrepareStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("DONE", done.getStatus());
        assertNotNull(done.getProjectPath());
        assertTrue(Files.exists(Paths.get(done.getProjectPath()).resolve("pom.xml")),
                "克隆的项目应就位");
        assertEquals(done.getProjectPath(), stub.compiledDir.toString(), "编译步骤应收到克隆目录");
        assertEquals("remote", done.getProjectName(), "项目名应取仓库末段并去掉 .git");
    }

    @Test
    void test_prepare_cloneFailure_marksFailed() throws Exception {
        GitPrepareService svc = service(new StubCompileService());

        GitPrepareStatus s = svc.prepare(request("file:///no/such/repo.git"));

        GitPrepareStatus failed = awaitTerminal(svc, s.getJobId());
        assertEquals("FAILED", failed.getStatus());
        assertNotNull(failed.getMessage());
        assertFalse(failed.getMessage().isEmpty());
    }

    @Test
    void test_prepare_compileFailure_marksFailed() throws Exception {
        Path bare = remoteWithProject();
        StubCompileService stub = new StubCompileService();
        stub.fail = true;
        GitPrepareService svc = service(stub);

        GitPrepareStatus s = svc.prepare(request(bare.toUri().toString()));

        GitPrepareStatus failed = awaitTerminal(svc, s.getJobId());
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getMessage().contains("模拟编译失败"), "应透传编译失败信息: " + failed.getMessage());
    }

    @Test
    void test_prepare_blankUrl_rejected() {
        GitPrepareService svc = service(new StubCompileService());
        assertThrows(AnalysisException.class, () -> svc.prepare(request("  ")));
        assertThrows(AnalysisException.class, () -> svc.prepare(null));
    }

    @Test
    void test_status_unknownJob_notFound() {
        GitPrepareService svc = service(new StubCompileService());
        assertThrows(AnalysisException.class, () -> svc.status("no-such-job"));
    }

    @Test
    void test_prepare_backendInSubdir_located() throws Exception {
        // 模拟前端+后端混合仓库：pom.xml 埋在 backend/ 子目录（不在一层）
        Path work = Files.createDirectories(temp.resolve("src"));
        Files.createDirectories(work.resolve("backend"));
        Files.write(work.resolve("backend/pom.xml"), "<project/>".getBytes());
        try (Git git = Git.init().setDirectory(work.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init")
                    .setAuthor("t", "t@t.com").setCommitter("t", "t@t.com").call();
        }
        Path bare = temp.resolve("remote-backend.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();
        }

        StubCompileService stub = new StubCompileService();
        GitPrepareService svc = service(stub);

        GitPrepareStatus s = svc.prepare(request(bare.toUri().toString()));

        GitPrepareStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("DONE", done.getStatus(), "应成功定位到 backend 子目录的 Maven 工程: " + done.getMessage());
        assertNotNull(done.getProjectPath());
        assertTrue(Files.exists(Paths.get(done.getProjectPath()).resolve("pom.xml")),
                "projectPath 应指向含 pom.xml 的 backend 目录: " + done.getProjectPath());
        assertTrue(Paths.get(done.getProjectPath()).getFileName().toString().equals("backend"),
                "应定位到 backend 子目录，而非仓库根: " + done.getProjectPath());
        assertEquals(done.getProjectPath(), stub.compiledDir.toString(),
                "编译应作用于 backend 目录");
    }

    @Test
    void test_repoDisplayName_variousUrls() {
        assertEquals("order-service",
                GitPrepareService.repoDisplayName("https://gitlab.com/group/order-service.git"));
        assertEquals("my-repo",
                GitPrepareService.repoDisplayName("https://github.com/user/my-repo"));
        assertEquals("inner",
                GitPrepareService.repoDisplayName("http://10.0.0.1/team/inner.git/"));
        assertEquals("remote",
                GitPrepareService.repoDisplayName("file:///X:/work/remote.git"));
        assertEquals("git-project",
                GitPrepareService.repoDisplayName("  "));
    }

    /** 制造一个"纯源码、无 pom/build.gradle"的远端正则仓库 */
    private Path remoteWithPlainJava() throws Exception {
        Path work = Files.createDirectories(temp.resolve("plainsrc"));
        Path javaDir = work.resolve("src/com/demo");
        Files.createDirectories(javaDir);
        Files.write(javaDir.resolve("Hello.java"),
                "package com.demo; public class Hello { public void hi(){} }".getBytes());
        try (Git git = Git.init().setDirectory(work.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init")
                    .setAuthor("t", "t@t.com").setCommitter("t", "t@t.com").call();
        }
        Path bare = temp.resolve("plain-remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();
        }
        return bare;
    }

    @Test
    void test_prepare_plainJavaSource_compiledByJavac() throws Exception {
        Path bare = remoteWithPlainJava();
        StubCompileService stub = new StubCompileService();
        StubJavacService javac = new StubJavacService();
        GitPrepareService svc = service(stub, javac);

        GitPrepareStatus s = svc.prepare(request(bare.toUri().toString()));

        GitPrepareStatus done = awaitTerminal(svc, s.getJobId());
        assertEquals("DONE", done.getStatus(), done.getMessage());
        // javac 编译收到克隆目录，且未走 mvn 编译
        assertNotNull(javac.srcDir, "纯源码工程应交给 javac 编译");
        assertNull(stub.compiledDir, "纯源码工程不应走 mvn compile");
        // 产物目录即项目根目录（build/classes 已被桩创建）
        assertTrue(Files.isDirectory(Paths.get(done.getProjectPath()).resolve("classes")),
                "projectPath 应指向含 classes 的产物目录: " + done.getProjectPath());
    }

    @Test
    void test_prepare_plainJava_javacFailure() throws Exception {
        Path bare = remoteWithPlainJava();
        StubJavacService javac = new StubJavacService();
        javac.fail = true;
        GitPrepareService svc = service(new StubCompileService(), javac);

        GitPrepareStatus s = svc.prepare(request(bare.toUri().toString()));

        GitPrepareStatus failed = awaitTerminal(svc, s.getJobId());
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getMessage().contains("模拟 javac 编译失败"),
                "应透传 javac 编译失败信息: " + failed.getMessage());
    }
}
