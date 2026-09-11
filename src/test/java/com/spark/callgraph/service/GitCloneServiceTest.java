package com.spark.callgraph.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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

    @Test
    void test_defaultUsername_githubVsOthers() {
        assertEquals("token", GitCloneService.defaultUsername("https://github.com/foo/bar.git"));
        assertEquals("oauth2", GitCloneService.defaultUsername("https://gitlab.com/foo/bar.git"));
        assertEquals("oauth2", GitCloneService.defaultUsername("http://10.0.0.1/group/repo.git"));
    }
}
