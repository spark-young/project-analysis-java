package com.spark.callgraph.testsupport;

import org.junit.jupiter.api.Assertions;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试夹具：把 src/test/resources/fixtures 下的 .java 源码现场编译为真实字节码。
 */
public final class Fixtures {

    private Fixtures() {}

    /** 编译 fixtures/<group> 全部源码到临时 classes 目录（可附加 classpath 条目） */
    public static Path compileToDir(String group, Path... classpathEntries) throws IOException {
        Path src = copySources(group);
        Path out = Files.createTempDirectory("fixtures-" + group + "-classes");
        compile(src, out, classpathEntries);
        return out;
    }

    /** 编译 fixtures/<group> 并打包为 jar */
    public static Path compileToJar(String group) throws IOException {
        Path classes = compileToDir(group);
        Path jar = Files.createTempFile("fixtures-" + group + "-", ".jar");
        zip(classes, jar);
        return jar;
    }

    private static Path copySources(String group) throws IOException {
        URL url = Fixtures.class.getClassLoader().getResource("fixtures/" + group);
        Assertions.assertNotNull(url, "夹具目录不存在: fixtures/" + group);
        Path srcRoot;
        try {
            srcRoot = Paths.get(url.toURI());
        } catch (Exception e) {
            throw new IOException("无法解析夹具目录: " + url, e);
        }
        Path tmp = Files.createTempDirectory("fixtures-" + group + "-src");
        try (Stream<Path> files = Files.walk(srcRoot)) {
            files.forEach(f -> {
                try {
                    Path rel = srcRoot.relativize(f);
                    Path target = tmp.resolve(rel.toString());
                    if (Files.isDirectory(f)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("复制夹具失败: " + f, e);
                }
            });
        }
        return tmp;
    }

    private static void compile(Path src, Path out, Path... classpathEntries) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assertions.assertNotNull(compiler, "测试需要 JDK（javac）");
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<File> files = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(src)) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> files.add(p.toFile()));
            }
            Assertions.assertFalse(files.isEmpty(), "夹具无源码文件: " + src);
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(out.toString());
            if (classpathEntries.length > 0) {
                StringBuilder cp = new StringBuilder();
                for (Path p : classpathEntries) {
                    if (cp.length() > 0) cp.append(File.pathSeparatorChar);
                    cp.append(p.toString());
                }
                options.add("-cp");
                options.add(cp.toString());
            }
            Boolean ok = compiler.getTask(null, fm, null, options, null,
                    fm.getJavaFileObjectsFromFiles(files)).call();
            Assertions.assertEquals(Boolean.TRUE, ok, "夹具编译失败: " + src);
        } catch (IOException e) {
            throw new RuntimeException("编译夹具失败", e);
        }
    }

    private static void zip(Path dir, Path jar) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar));
             Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                try {
                    String entry = dir.relativize(f).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(f, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("打包失败: " + f, e);
                }
            });
        }
    }
}
