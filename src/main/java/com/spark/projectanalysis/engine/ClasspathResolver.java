package com.spark.projectanalysis.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 项目布局识别：Maven / Gradle / fat jar / 普通 jar / classes+lib / 裸 classes。
 */
public final class ClasspathResolver {

    private ClasspathResolver() {}

    public static ProjectLayout resolve(Path projectPath, Path mavenRepo) throws IOException {
        List<String> warnings = new ArrayList<>();
        if (projectPath == null || !Files.exists(projectPath)) {
            throw new IllegalArgumentException("路径不存在: " + projectPath);
        }

        // jar / war 文件
        if (Files.isRegularFile(projectPath)) {
            String name = projectPath.getFileName().toString().toLowerCase();
            if (name.endsWith(".jar") || name.endsWith(".war")) {
                return resolveJar(projectPath, warnings);
            }
            throw new IllegalArgumentException("不支持的文件类型（仅支持 .jar/.war 或目录）: " + name);
        }

        // 目录
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("既不是目录也不是 jar: " + projectPath);
        }
        return resolveDirectory(projectPath, mavenRepo, warnings);
    }

    // ------------------------------------------------------------------
    // jar 布局
    // ------------------------------------------------------------------

    private static ProjectLayout resolveJar(Path jar, List<String> warnings) throws IOException {
        // fat jar：BOOT-INF/classes + BOOT-INF/lib
        Path tempDir = Files.createTempDirectory("fatjar-");
        List<Path> classDirs = new ArrayList<>();
        List<Path> depJars = new ArrayList<>();
        boolean hasBootInf = false;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("BOOT-INF/") && !e.isDirectory()) {
                    hasBootInf = true;
                    break;
                }
            }
        }
        if (!hasBootInf) {
            // 普通 jar：整体解压为一个项目 classes 目录
            Path out = Files.createTempDirectory("plainjar-");
            unzipInto(jar, out, "", null);
            classDirs.add(out);
            return new ProjectLayout(ProjectLayout.LayoutType.PLAIN_JAR, jar,
                    stripExt(jar.getFileName().toString()), classDirs, depJars, warnings,
                    new ArrayList<>(java.util.Arrays.asList(out)));
        }

        // fat jar
        Path bootClasses = tempDir.resolve("classes");
        Path bootLib = tempDir.resolve("lib");
        Files.createDirectories(bootClasses);
        Files.createDirectories(bootLib);
        unzipInto(jar, bootClasses, "BOOT-INF/classes/", null);
        List<String> libNames = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("BOOT-INF/lib/") && e.getName().endsWith(".jar")) {
                    libNames.add(e.getName());
                }
            }
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String lib : libNames) {
                ZipEntry e = zip.getEntry(lib);
                if (e == null) continue;
                Path out = bootLib.resolve(lib.substring("BOOT-INF/lib/".length()));
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                depJars.add(out);
            }
        }
        classDirs.add(bootClasses);
        List<Path> temps = new ArrayList<>();
        temps.add(tempDir);
        return new ProjectLayout(ProjectLayout.LayoutType.FAT_JAR, jar,
                stripExt(jar.getFileName().toString()), classDirs, depJars, warnings, temps);
    }

    private static void unzipInto(Path jar, Path outDir, String prefix, Comparator<String> unused)
            throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (prefix != null && !prefix.isEmpty()) {
                    if (!n.startsWith(prefix)) continue;
                    n = n.substring(prefix.length());
                } else {
                    // 跳过目录条目元数据
                    if (n.startsWith("META-INF/") && !n.endsWith(".class")) continue;
                }
                Path target = outDir.resolve(n).normalize();
                if (!target.startsWith(outDir)) continue; // 防路径穿越
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ------------------------------------------------------------------
    // 目录布局
    // ------------------------------------------------------------------

    private static ProjectLayout resolveDirectory(Path dir, Path mavenRepo, List<String> warnings)
            throws IOException {
        String projectName = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();

        // Maven（单模块 / 多模块聚合）：收集全部模块的 target/classes，依赖取各 pom 并集
        boolean mavenAtRoot = Files.isRegularFile(dir.resolve("pom.xml"))
                || Files.isDirectory(dir.resolve("target/classes"));
        if (mavenAtRoot) {
            List<Path> classesDirs = new ArrayList<>();
            collectMavenClassesDirs(dir, 0, classesDirs);
            if (!classesDirs.isEmpty()) {
                List<Path> deps = resolveAllMavenDeps(dir, mavenRepo, warnings);
                return new ProjectLayout(ProjectLayout.LayoutType.MAVEN, dir, projectName,
                        classesDirs, deps, warnings, new ArrayList<>());
            }
        }

        // Gradle（单模块 / 多模块）：收集全部模块的 build/classes/java/main
        boolean gradleAtRoot = Files.isRegularFile(dir.resolve("build.gradle"))
                || Files.isDirectory(dir.resolve("build/classes/java/main"));
        if (gradleAtRoot) {
            List<Path> classesDirs = new ArrayList<>();
            collectGradleClassesDirs(dir, 0, classesDirs);
            if (!classesDirs.isEmpty()) {
                warnings.add("Gradle 项目的依赖暂未自动解析（可用 fat jar 或 classes+lib 方式），当前仅分析项目自身类。");
                return new ProjectLayout(ProjectLayout.LayoutType.GRADLE, dir, projectName,
                        classesDirs, new ArrayList<>(), warnings, new ArrayList<>());
            }
        }

        // classes + lib（含 WEB-INF 布局）
        Path classesDir = firstExisting(dir, "classes", "WEB-INF/classes");
        Path libDir = firstExisting(dir, "lib", "WEB-INF/lib");
        if (classesDir != null) {
            List<Path> deps = new ArrayList<>();
            if (libDir != null) {
                try (Stream<Path> jars = Files.list(libDir)) {
                    jars.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                            .sorted().forEach(deps::add);
                }
            }
            return new ProjectLayout(ProjectLayout.LayoutType.CLASSES_WITH_LIB, dir, projectName,
                    list(classesDir), deps, warnings, new ArrayList<>());
        }

        // lib 目录里的 jar 直接作为项目？—— 视为无项目类
        // 裸 classes：目录树中直接包含 .class
        if (containsClassFile(dir, 3)) {
            return new ProjectLayout(ProjectLayout.LayoutType.RAW_CLASSES, dir, projectName,
                    list(dir), new ArrayList<>(), warnings, new ArrayList<>());
        }

        throw new IllegalArgumentException(
                "未识别的项目布局（未找到 target/classes、build/classes、classes+lib 或 .class 文件）: " + dir
                        + "。请先编译项目（如 mvn compile）或提供 jar。");
    }

    /** 递归收集 Maven 各模块的 target/classes（不深入 target 内部，跳过 .git/.svn 等） */
    private static void collectMavenClassesDirs(Path dir, int depth, List<Path> out) throws IOException {
        if (depth > 8) return;
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) {
                if (!Files.isDirectory(child)) continue;
                String name = child.getFileName().toString();
                if (name.equals(".git") || name.equals(".svn") || name.equals(".idea")) continue;
                if (name.equals("target")) {
                    Path classes = child.resolve("classes");
                    if (Files.isDirectory(classes)) out.add(classes);
                    continue;
                }
                collectMavenClassesDirs(child, depth + 1, out);
            }
        }
    }

    /** 递归收集 Gradle 各模块的 build/classes/java/main */
    private static void collectGradleClassesDirs(Path dir, int depth, List<Path> out) throws IOException {
        if (depth > 8) return;
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) {
                if (!Files.isDirectory(child)) continue;
                String name = child.getFileName().toString();
                if (name.equals(".git") || name.equals(".svn") || name.equals(".idea")) continue;
                if (name.equals("build")) {
                    Path classes = child.resolve("classes/java/main");
                    if (Files.isDirectory(classes)) out.add(classes);
                    continue;
                }
                collectGradleClassesDirs(child, depth + 1, out);
            }
        }
    }

    /** 收集全部模块 pom 并解析依赖，按 jar 路径去重取并集 */
    private static List<Path> resolveAllMavenDeps(Path dir, Path repo, List<String> warnings) throws IOException {
        List<Path> poms = new ArrayList<>();
        collectPomFiles(dir, 0, poms);
        java.util.LinkedHashSet<Path> jars = new java.util.LinkedHashSet<>();
        for (Path pom : poms) {
            jars.addAll(PomDependencyResolver.resolve(pom.getParent(), repo, warnings));
        }
        // 多模块重复解析可能产生重复警告，去重保持顺序
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(warnings);
        warnings.clear();
        warnings.addAll(uniq);
        return new ArrayList<>(jars);
    }

    private static void collectPomFiles(Path dir, int depth, List<Path> out) throws IOException {
        if (depth > 8) return;
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) {
                if (!Files.isDirectory(child)) continue;
                String name = child.getFileName().toString();
                if (name.equals(".git") || name.equals(".svn") || name.equals(".idea")
                        || name.equals("target") || name.equals("build")) continue;
                collectPomFiles(child, depth + 1, out);
            }
        }
        Path pom = dir.resolve("pom.xml");
        if (Files.isRegularFile(pom)) out.add(pom);
    }

    private static Path firstExisting(Path dir, String... names) {
        for (String n : names) {
            Path p = dir.resolve(n);
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    private static boolean containsClassFile(Path dir, int maxDepth) throws IOException {
        try (Stream<Path> walk = Files.walk(dir, maxDepth)) {
            return walk.anyMatch(p -> p.toString().endsWith(".class"));
        }
    }

    private static List<Path> list(Path p) {
        List<Path> out = new ArrayList<>();
        out.add(p);
        return out;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
