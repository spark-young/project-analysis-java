package com.spark.projectanalysis.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** mvn 编译：真实调用本机 mvn（无 mvn 环境自动跳过）。 */
class MavenCompileServiceTest {

    @TempDir
    Path temp;

    private static boolean mvnOnPath() {
        String path = System.getenv("PATH");
        if (path == null) return false;
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            String d = dir.replace("\"", "").trim();
            if (d.isEmpty()) continue;
            if (win) {
                if (new File(d, "mvn.cmd").exists() || new File(d, "mvn.bat").exists()) return true;
            } else if (new File(d, "mvn").exists()) {
                return true;
            }
        }
        return false;
    }

    private Path tinyProject(String javaSource) throws Exception {
        Path project = temp.resolve("tiny");
        Files.createDirectories(project.resolve("src/main/java/com/tiny"));
        Files.write(project.resolve("pom.xml"), (
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                        + "<modelVersion>4.0.0</modelVersion><groupId>com.tiny</groupId>"
                        + "<artifactId>tiny</artifactId><version>1.0</version><packaging>jar</packaging>"
                        + "<properties><maven.compiler.source>8</maven.compiler.source>"
                        + "<maven.compiler.target>8</maven.compiler.target></properties>"
                        + "</project>").getBytes(StandardCharsets.UTF_8));
        Files.write(project.resolve("src/main/java/com/tiny/Hello.java"),
                javaSource.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    @Test
    void test_compile_simpleProject_succeeds() throws Exception {
        Assumptions.assumeTrue(mvnOnPath(), "环境无 mvn，跳过");
        Path project = tinyProject("package com.tiny;\npublic class Hello { }");

        MavenCompileService.CompileResult r = new MavenCompileService().compile(project);

        assertTrue(r.isSuccess(), "编译应成功: " + r.getOutputTail());
        assertTrue(Files.exists(project.resolve("target/classes/com/tiny/Hello.class")),
                "应产出 target/classes");
    }

    @Test
    void test_compile_brokenSource_failsWithOutput() throws Exception {
        Assumptions.assumeTrue(mvnOnPath(), "环境无 mvn，跳过");
        Path project = tinyProject("package com.tiny;\npublic class Hello { broken !!!");

        MavenCompileService.CompileResult r = new MavenCompileService().compile(project);

        assertFalse(r.isSuccess(), "语法错误应编译失败");
        assertNotNull(r.getOutputTail());
        assertFalse(r.getOutputTail().isEmpty(), "失败时应携带 mvn 输出便于排错");
    }
}
