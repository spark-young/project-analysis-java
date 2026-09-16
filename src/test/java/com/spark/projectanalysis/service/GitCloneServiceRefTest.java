package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.dto.GitRefs;
import com.spark.projectanalysis.service.dto.GitSwitchResult;
import com.spark.projectanalysis.service.dto.RemoteStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分支/Tag 切换 + 远端更新检测：用本地 bare 仓库模拟远端，无需网络。
 * 复用 GitCloneServiceTest 的夹具模式（JGit init + push）。
 */
class GitCloneServiceRefTest {

    @TempDir
    Path temp;

    private Path initWorkRepo() throws Exception {
        Path work = Files.createDirectories(temp.resolve("src"));
        Files.write(work.resolve("pom.xml"), "<project/>".getBytes());
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

    private void commit(Path work, String message) throws Exception {
        try (Git git = Git.open(work.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message)
                    .setAuthor("t", "t@t.com").setCommitter("t", "t@t.com").call();
        }
    }

    private Path pushToBare(Path work) throws Exception {
        Path bare = temp.resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().setPushTags().call();
        }
        return bare;
    }

    private Path cloneBare(Path bare) throws Exception {
        Path target = temp.resolve("cloned");
        new GitCloneService().clone(bare.toUri().toString(), null, null, null, target);
        return target;
    }

    private String gitCli(Path repoDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repoDir.toString());
        for (String a : args) cmd.add(a);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] buf = p.getInputStream().readAllBytes();
        int code = p.waitFor();
        String out = new String(buf, StandardCharsets.UTF_8);
        if (code != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " 失败：\n" + out);
        }
        return out;
    }

    @Test
    void test_listRefs_returnsBranchesAndTags() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("feature-x").call();
            git.tag().setName("v1.0").call();
        }
        Path bare = pushToBare(work);

        GitRefs refs = new GitCloneService().listRefs(bare.toUri().toString(), null, null);

        assertTrue(refs.getBranches().contains("feature-x"), "应包含 feature-x 分支");
        assertTrue(refs.getTags().contains("v1.0"), "应包含 v1.0 标签");
        assertNotNull(refs.getDefaultBranch(), "应识别默认分支");
        assertTrue(refs.getBranches().contains(refs.getDefaultBranch()),
                "默认分支应属于分支列表");
    }

    @Test
    void test_switchRef_toBranch() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("dev").call();
        }
        Path bare = pushToBare(work);
        Path target = cloneBare(bare);

        GitSwitchResult r = new GitCloneService()
                .switchRef(target, bare.toUri().toString(), "dev", "BRANCH", null, null, null);

        try (Git git = Git.open(target.toFile())) {
            assertEquals("dev", git.getRepository().getBranch(), "应切换到 dev 分支");
        }
        assertFalse(r.isStashed(), "工作区干净时不应 stash");
        assertFalse(r.isConflict());
    }

    @Test
    void test_switchRef_toTag() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.tag().setName("v1").call();
        }
        Path bare = pushToBare(work);
        Path target = cloneBare(bare);

        GitSwitchResult r = new GitCloneService()
                .switchRef(target, bare.toUri().toString(), "v1", "TAG", null, null, null);

        try (Git git = Git.open(target.toFile())) {
            org.eclipse.jgit.lib.Ref head = git.getRepository().exactRef("HEAD");
            assertNotNull(head);
            assertFalse(head.isSymbolic(), "Tag 检出后应为 detached HEAD");
            String headSha = git.getRepository().resolve("HEAD").getName();
            String tagCommit = git.getRepository().resolve("refs/tags/v1^{commit}").getName();
            assertEquals(tagCommit, headSha, "HEAD 应指向 Tag 提交点");
        }
        assertEquals("v1", r.getRef());
        assertEquals("TAG", r.getRefType());
    }

    @Test
    void test_switchRef_stashAndPopKeepsChanges() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("dev").call();
        }
        Path bare = pushToBare(work);
        Path target = cloneBare(bare);

        Path wip = target.resolve("WIP.txt");
        Files.write(wip, "keep me".getBytes());

        GitSwitchResult r = new GitCloneService()
                .switchRef(target, bare.toUri().toString(), "dev", "BRANCH", null, null, null);

        assertTrue(r.isStashed(), "脏工作区应自动 stash");
        assertTrue(r.isStashPopped(), "切换后应自动 pop");
        assertEquals("keep me", new String(Files.readAllBytes(wip), StandardCharsets.UTF_8),
                "未提交改动应被保留");
        try (Git git = Git.open(target.toFile())) {
            assertEquals("dev", git.getRepository().getBranch());
        }
    }

    @Test
    void test_switchRef_dirtyConflict_keepsStash() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("dev").call();
            git.checkout().setName("dev").call();
        }
        Path demo = work.resolve("src/main/java/com/git/demo/Demo.java");
        Files.write(demo, "package com.git.demo; public class Demo { int devField; }".getBytes());
        commit(work, "dev change");
        try (Git git = Git.open(work.toFile())) {
            git.checkout().setName("master").call();
        }
        Path bare = pushToBare(work);
        Path target = cloneBare(bare);

        // 本地对同一文件做同位置修改 → stash pop 必然冲突
        Files.write(target.resolve("src/main/java/com/git/demo/Demo.java"),
                "package com.git.demo; public class Demo { int localField; }".getBytes());

        GitSwitchResult r = new GitCloneService()
                .switchRef(target, bare.toUri().toString(), "dev", "BRANCH", null, null, null);

        assertTrue(r.isStashed(), "应已 stash");
        assertTrue(r.isConflict(), "应识别出冲突");
        try (Git git = Git.open(target.toFile())) {
            assertEquals("dev", git.getRepository().getBranch(), "切换本身应成功");
        }
        String stashes = gitCli(target, "stash", "list");
        assertTrue(stashes.contains("callgraph-autoswitch"), "冲突时 stash 应保留，改动不丢失");
    }

    @Test
    void test_checkRemoteUpdate_upToDate_and_behind() throws Exception {
        Path work = initWorkRepo();
        Path bare = pushToBare(work);
        Path target = cloneBare(bare);
        GitCloneService svc = new GitCloneService();

        RemoteStatus up = svc.checkRemoteUpdate(target, bare.toUri().toString(),
                "master", "BRANCH", null, null);
        assertEquals("UP_TO_DATE", up.getStatus(), "刚克隆应是最新");
        assertNotNull(up.getLocalSha());
        assertTrue(up.getCheckedAt() > 0);

        // 远端新增一次提交
        Files.write(work.resolve("pom.xml"), "<project><!-- v2 --></project>".getBytes());
        commit(work, "v2");
        try (Git git = Git.open(work.toFile())) {
            git.push().setRemote("origin").call();
        }

        RemoteStatus behind = svc.checkRemoteUpdate(target, bare.toUri().toString(),
                "master", "BRANCH", null, null);
        assertEquals("BEHIND", behind.getStatus(), "远端有更新应检测为 BEHIND");
        assertNotEquals(behind.getLocalSha(), behind.getRemoteSha());
    }
}
