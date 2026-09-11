package com.spark.callgraph.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathResolverTest {

    @TempDir
    Path temp;

    @Test
    void test_mavenLayout_detected() throws IOException {
        Path project = temp.resolve("myapp");
        Files.createDirectories(project.resolve("target/classes/com/foo"));
        Files.write(project.resolve("pom.xml"), "<project/>".getBytes());
        try (ProjectLayout layout = ClasspathResolver.resolve(project, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.MAVEN, layout.getType());
            assertEquals(1, layout.getProjectClassDirs().size());
            assertTrue(layout.getProjectClassDirs().get(0).endsWith("target/classes"));
        }
    }

    @Test
    void test_multiModuleMaven_allModuleClassesCollected() throws IOException {
        Path root = temp.resolve("multi");
        Path moduleA = root.resolve("module-a");
        Path moduleB = root.resolve("module-b");
        Files.createDirectories(moduleA.resolve("target/classes/com/multi/a"));
        Files.createDirectories(moduleB.resolve("target/classes/com/multi/b"));
        Files.write(moduleA.resolve("target/classes/com/multi/a/AService.class"), new byte[0]);
        Files.write(moduleB.resolve("target/classes/com/multi/b/BService.class"), new byte[0]);
        Files.write(root.resolve("pom.xml"), (
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.multi</groupId>"
                        + "<artifactId>multi</artifactId><version>1.0</version><packaging>pom</packaging>"
                        + "<modules><module>module-a</module><module>module-b</module></modules></project>")
                .getBytes());
        Files.write(moduleA.resolve("pom.xml"), modulePom("module-a").getBytes());
        Files.write(moduleB.resolve("pom.xml"), modulePom("module-b").getBytes());

        try (ProjectLayout layout = ClasspathResolver.resolve(root, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.MAVEN, layout.getType());
            assertEquals(2, layout.getProjectClassDirs().size(), "两个模块的 target/classes 都应收集");
            assertTrue(layout.getProjectClassDirs().stream().anyMatch(p -> p.endsWith("module-a/target/classes"))
                    || layout.getProjectClassDirs().stream()
                        .anyMatch(p -> p.toString().replace('\\', '/').endsWith("module-a/target/classes")));
        }
    }

    @Test
    void test_multiModuleMaven_dependencyUnionAcrossModules() throws IOException {
        // 假仓库：com.fake:lib:1.0 的空 jar
        Path repo = temp.resolve("repo");
        Path libJar = repo.resolve("com/fake/lib/1.0/lib-1.0.jar");
        Files.createDirectories(libJar.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(libJar))) {
            zos.putNextEntry(new ZipEntry("com/fake/Lib.class"));
            zos.write(new byte[]{1});
            zos.closeEntry();
        }

        Path root = temp.resolve("multi2");
        Path moduleA = root.resolve("module-a");
        Path moduleB = root.resolve("module-b");
        Files.createDirectories(moduleA.resolve("target/classes/com/multi/a"));
        Files.createDirectories(moduleB.resolve("target/classes/com/multi/b"));
        Files.write(moduleA.resolve("pom.xml"), modulePom("module-a").getBytes());
        Files.write(moduleB.resolve("pom.xml"), (modulePom("module-b")
                .replace("</project>",
                        "<dependencies><dependency><groupId>com.fake</groupId><artifactId>lib</artifactId>"
                                + "<version>1.0</version></dependency></dependencies></project>"))
                .getBytes());
        Files.write(root.resolve("pom.xml"), (
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.multi</groupId>"
                        + "<artifactId>multi</artifactId><version>1.0</version><packaging>pom</packaging>"
                        + "<modules><module>module-a</module><module>module-b</module></modules></project>")
                .getBytes());

        try (ProjectLayout layout = ClasspathResolver.resolve(root, repo)) {
            assertEquals(ProjectLayout.LayoutType.MAVEN, layout.getType());
            assertTrue(layout.getDependencyJars().stream().anyMatch(j -> j.getFileName().toString().equals("lib-1.0.jar")),
                    "模块声明的依赖应进入依赖并集");
        }
    }

    private static String modulePom(String artifactId) {
        return "<project><modelVersion>4.0.0</modelVersion>"
                + "<parent><groupId>com.multi</groupId><artifactId>multi</artifactId><version>1.0</version></parent>"
                + "<artifactId>" + artifactId + "</artifactId></project>";
    }

    @Test
    void test_gradleLayout_detected() throws IOException {
        Path project = temp.resolve("gradleapp");
        Files.createDirectories(project.resolve("build/classes/java/main/com/foo"));
        try (ProjectLayout layout = ClasspathResolver.resolve(project, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.GRADLE, layout.getType());
            assertFalse(layout.getWarnings().isEmpty(), "Gradle 应提示依赖未解析");
        }
    }

    @Test
    void test_classesWithLibLayout_detected() throws IOException {
        Path project = temp.resolve("webapp/WEB-INF");
        Files.createDirectories(project.resolve("classes/com/foo"));
        Files.createDirectories(project.resolve("lib"));
        Files.write(project.resolve("lib/a.jar"), new byte[0]);
        Files.write(project.resolve("lib/b.jar"), new byte[0]);
        try (ProjectLayout layout = ClasspathResolver.resolve(project, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.CLASSES_WITH_LIB, layout.getType());
            assertEquals(2, layout.getDependencyJars().size());
        }
    }

    @Test
    void test_rawClassesLayout_detected() throws IOException {
        Path project = temp.resolve("raw");
        Files.createDirectories(project);
        Files.write(project.resolve("A.class"), new byte[0]);
        try (ProjectLayout layout = ClasspathResolver.resolve(project, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.RAW_CLASSES, layout.getType());
            assertEquals(0, layout.getDependencyJars().size());
        }
    }

    @Test
    void test_fatJarLayout_detectedAndExtracted() throws IOException {
        Path jar = temp.resolve("app.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("BOOT-INF/classes/com/foo/App.class"));
            zos.write(new byte[]{1});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("BOOT-INF/lib/dep-a.jar"));
            zos.write(new byte[]{2});
            zos.closeEntry();
        }
        try (ProjectLayout layout = ClasspathResolver.resolve(jar, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.FAT_JAR, layout.getType());
            assertEquals(1, layout.getProjectClassDirs().size());
            assertEquals(1, layout.getDependencyJars().size());
            assertTrue(Files.exists(layout.getProjectClassDirs().get(0).resolve("com/foo/App.class")));
        }
    }

    @Test
    void test_plainJarLayout() throws IOException {
        Path jar = temp.resolve("lib.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("com/foo/Foo.class"));
            zos.write(new byte[]{1});
            zos.closeEntry();
        }
        try (ProjectLayout layout = ClasspathResolver.resolve(jar, temp.resolve("repo"))) {
            assertEquals(ProjectLayout.LayoutType.PLAIN_JAR, layout.getType());
            assertTrue(Files.exists(layout.getProjectClassDirs().get(0).resolve("com/foo/Foo.class")));
        }
    }

    @Test
    void test_nonExistentPath_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathResolver.resolve(temp.resolve("no-such"), temp.resolve("repo")));
    }

    @Test
    void test_unsupportedFileType_rejected() throws IOException {
        Path file = temp.resolve("x.txt");
        Files.write(file, new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathResolver.resolve(file, temp.resolve("repo")));
    }
}
