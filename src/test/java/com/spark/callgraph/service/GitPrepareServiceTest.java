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
        GitPrepareService svc = new GitPrepareService(new GitCloneService(), stub) {
            @Override
            protected Path createWorkDir() throws java.io.IOException {
                Path dir = Files.createTempDirectory(temp, "git-");
                return dir;
            }
        };
        return svc;
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
}
