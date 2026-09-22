package com.spark.projectanalysis.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** JGit 克隆：用本地 bare 仓库模拟远端，无需网络。 */
class GitCloneServiceTest {

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

    private Path pushToBare(Path work) throws Exception {
        Path bare = temp.resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) { }
        try (Git git = Git.open(work.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();
        }
        return bare;
    }

    @Test
    void test_cloneDefaultBranch() throws Exception {
        Path work = initWorkRepo();
        Path bare = pushToBare(work);
        Path target = temp.resolve("cloned");

        new GitCloneService().clone(bare.toUri().toString(), null, null, null, target);

        assertTrue(Files.exists(target.resolve("pom.xml")), "克隆应包含仓库文件");
        assertTrue(Files.exists(target.resolve(".git")));

        // 工作区确实被填充（默认克隆正常检出）
        assertTrue(Files.exists(target.resolve("src/main/java/com/git/demo/Demo.java")),
                "克隆后工作区不能为空");

        // OPT-16：克隆后仓库配置应禁用符号链接。
        // 注意这只是后置条件；"配置是否早于 checkout 生效" 的端到端判定受本机限制，
        // 详见 test_clone_symlinkEntry_isNotMaterializedAsSymlink 的说明。
        try (Git git = Git.open(target.toFile())) {
            assertFalse(git.getRepository().getConfig().getBoolean("core", null, "symlinks", true),
                    "克隆应禁用符号链接（core.symlinks=false）");
        }
    }

    @Test
    void test_cloneSpecificBranch() throws Exception {
        Path work = initWorkRepo();
        try (Git git = Git.open(work.toFile())) {
            git.branchCreate().setName("dev").call();
        }
        Path bare = pushToBare(work);
        Path target = temp.resolve("cloned-dev");

        new GitCloneService().clone(bare.toUri().toString(), "dev", null, null, target);

        try (Git git = Git.open(target.toFile())) {
            assertEquals("dev", git.getRepository().getBranch(), "应检出指定分支");
        }
    }

    /**
     * 造一个含 symlink 条目（mode 120000）的 bare 远端。
     * <p>
     * 直接用 JGit 低层 API 往对象库写 tree/commit，而不是在本机创建真实符号链接 ——
     * Windows 上创建符号链接需要管理员权限（本机实测 "此操作需要管理员权限"），
     * 但漏洞场景关心的是**仓库里存在 symlink 条目**，与产物怎么造出来无关。
     */
    private Path remoteWithSymlinkEntry() throws Exception {
        Path bare = temp.resolve("sym-remote.git");
        try (Git git = Git.init().setBare(true).setDirectory(bare.toFile()).call()) {
            Repository repo = git.getRepository();
            try (ObjectInserter inserter = repo.newObjectInserter()) {
                ObjectId blob = inserter.insert(Constants.OBJ_BLOB,
                        "pom.xml".getBytes(StandardCharsets.UTF_8));

                TreeFormatter tree = new TreeFormatter();
                tree.append("README.md", FileMode.REGULAR_FILE, blob);
                // symlink 条目：内容即链接目标，mode = 120000
                tree.append("linked-file", FileMode.SYMLINK, blob);
                ObjectId treeId = inserter.insert(tree);

                PersonIdent who = new PersonIdent("t", "t@t.com");
                CommitBuilder commit = new CommitBuilder();
                commit.setTreeId(treeId);
                commit.setAuthor(who);
                commit.setCommitter(who);
                commit.setMessage("add symlink entry");
                ObjectId commitId = inserter.insert(commit);
                inserter.flush();

                RefUpdate head = repo.updateRef("refs/heads/master");
                head.setNewObjectId(commitId);
                head.setRefLogMessage("commit", false);
                head.forceUpdate();
            }
        }
        return bare;
    }

    /**
     * OPT-16：远端含 symlink 条目（mode 120000），克隆后该条目必须落成**普通文件**而非符号链接。
     * <p>
     * ⚠️ 本机限制（已实测）：Windows 下 {@code FS.DETECTED.supportsSymlinks() == false}，
     * 且 JGit 在 init 阶段即按该能力写入 {@code core.symlinks=false}。因此本用例**无法**端到端
     * 区分"配置写在 checkout 之前"与"写在 call() 之后"——两种写法在本机产物相同
     * （对照组：不带任何加固的默认克隆同样落成普通文件）。
     * <p>
     * 故本用例定位为**回归护栏**：确认加固后条目仍是普通文件、内容为链接目标、且仓库配置为 false。
     * "配置早于 checkout 生效" 由 {@code GitCloneService} 采用的机制保证，依据（已在 JGit 5.1.3 独立验证）：
     * (1) {@code CloneCommand.call()} 字节码顺序为 verifyDirectories → fetch（打开传输、触发传输回调）→ checkout；
     * (2) 回调内写入的 {@code core.symlinks} 会被仓库 Config 按文件快照自动重载，对随后的
     *     {@code DirCacheCheckout} 立即可见。
     */
    @Test
    void test_clone_symlinkEntry_isNotMaterializedAsSymlink() throws Exception {
        Path bare = remoteWithSymlinkEntry();
        Path target = temp.resolve("cloned-sym");

        new GitCloneService().clone(bare.toUri().toString(), null, null, null, target);

        Path entry = target.resolve("linked-file");
        assertTrue(Files.exists(entry, LinkOption.NOFOLLOW_LINKS),
                "symlink 条目应被检出（内容落成普通文件）: " + entry);
        assertFalse(Files.isSymbolicLink(entry),
                "core.symlinks=false 时 symlink 条目不得落成符号链接");
        assertEquals("pom.xml", new String(Files.readAllBytes(entry), StandardCharsets.UTF_8),
                "禁用符号链接后，应把链接目标作为普通文件内容写入");
        try (Git git = Git.open(target.toFile())) {
            assertFalse(git.getRepository().getConfig().getBoolean("core", null, "symlinks", true),
                    "克隆应禁用符号链接（core.symlinks=false）");
        }
    }

    @Test
    void test_defaultUsername_githubVsOthers() {
        assertEquals("token", GitCloneService.defaultUsername("https://github.com/foo/bar.git"));
        assertEquals("oauth2", GitCloneService.defaultUsername("https://gitlab.com/foo/bar.git"));
        assertEquals("oauth2", GitCloneService.defaultUsername("http://10.0.0.1/group/repo.git"));
    }
}
