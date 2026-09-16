package com.spark.projectanalysis.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PomDependencyResolverTest {

    @TempDir
    Path temp;
    Path repo;
    Path project;

    @BeforeEach
    void setUp() throws IOException {
        repo = temp.resolve("repo");
        project = temp.resolve("app");
        Files.createDirectories(project);
        Files.createDirectories(repo);
    }

    private void writeRepoPom(String groupId, String artifactId, String version, String body) throws IOException {
        Path dir = repo.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
        Files.createDirectories(dir);
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId>"
                + "<version>" + version + "</version><packaging>pom</packaging>"
                + body + "</project>";
        Files.write(dir.resolve(artifactId + "-" + version + ".pom"),
                pom.getBytes(StandardCharsets.UTF_8));
    }

    private void writeRepoJar(String groupId, String artifactId, String version) throws IOException {
        Path dir = repo.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
        Files.createDirectories(dir);
        Files.write(dir.resolve(artifactId + "-" + version + ".jar"), new byte[0]);
    }

    @Test
    void test_parentInheritance_properties_and_dependencyManagement() throws IOException {
        writeRepoPom("com.demo", "parent", "1.0",
                "<properties><demo.version>1.2</demo.version></properties>"
                + "<dependencyManagement><dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-a</artifactId>"
                + "<version>${demo.version}</version></dependency>"
                + "</dependencies></dependencyManagement>");
        writeRepoJar("com.demo", "lib-a", "1.2");
        writeRepoJar("com.demo", "lib-b", "1.0");

        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<parent><groupId>com.demo</groupId><artifactId>parent</artifactId><version>1.0</version></parent>"
                + "<groupId>com.demo</groupId><artifactId>app</artifactId><version>0.1</version>"
                + "<dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-a</artifactId></dependency>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-b</artifactId><version>1.0</version></dependency>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-test</artifactId><version>1.0</version><scope>test</scope></dependency>"
                + "</dependencies></project>";
        Files.write(project.resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));

        List<String> warnings = new ArrayList<>();
        List<Path> jars = PomDependencyResolver.resolve(project, repo, warnings);

        assertEquals(2, jars.size(), "lib-a(经父pom依赖管理+属性) + lib-b");
        assertTrue(jars.stream().anyMatch(p -> p.toString().endsWith("lib-a-1.2.jar")));
        assertTrue(jars.stream().anyMatch(p -> p.toString().endsWith("lib-b-1.0.jar")));
        assertTrue(jars.stream().noneMatch(p -> p.toString().contains("lib-test")), "test 作用域应排除");
    }

    @Test
    void test_bomImport_resolution() throws IOException {
        writeRepoPom("com.demo", "bom", "2.0",
                "<dependencyManagement><dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-c</artifactId><version>2.1</version></dependency>"
                + "</dependencies></dependencyManagement>");
        writeRepoJar("com.demo", "lib-c", "2.1");

        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>com.demo</groupId><artifactId>app</artifactId><version>0.1</version>"
                + "<dependencyManagement><dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>bom</artifactId><version>2.0</version>"
                + "<type>pom</type><scope>import</scope></dependency>"
                + "</dependencies></dependencyManagement>"
                + "<dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>lib-c</artifactId></dependency>"
                + "</dependencies></project>";
        Files.write(project.resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));

        List<String> warnings = new ArrayList<>();
        List<Path> jars = PomDependencyResolver.resolve(project, repo, warnings);
        assertEquals(1, jars.size());
        assertTrue(jars.get(0).toString().endsWith("lib-c-2.1.jar"));
    }

    @Test
    void test_missingDependency_reportedInWarnings() throws IOException {
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>com.demo</groupId><artifactId>app</artifactId><version>0.1</version>"
                + "<dependencies>"
                + "<dependency><groupId>com.demo</groupId><artifactId>missing-d</artifactId><version>1.0</version></dependency>"
                + "</dependencies></project>";
        Files.write(project.resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));

        List<String> warnings = new ArrayList<>();
        List<Path> jars = PomDependencyResolver.resolve(project, repo, warnings);
        assertEquals(0, jars.size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("missing-d")), "缺失依赖应记入告警");
    }

    @Test
    void test_noPom_returnsEmptyWithWarning() {
        List<String> warnings = new ArrayList<>();
        List<Path> jars = PomDependencyResolver.resolve(project, repo, warnings);
        assertTrue(jars.isEmpty());
        assertFalse(warnings.isEmpty());
    }
}
