package com.spark.projectanalysis.engine;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 识别后的被分析项目布局。
 */
public final class ProjectLayout implements Closeable {

    public enum LayoutType {
        MAVEN("Maven（target/classes + 本地仓库依赖）"),
        GRADLE("Gradle（build/classes）"),
        FAT_JAR("Spring Boot fat jar"),
        PLAIN_JAR("普通 jar"),
        CLASSES_WITH_LIB("classes + lib 目录"),
        RAW_CLASSES("裸 classes 目录");

        private final String label;

        LayoutType(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private final LayoutType type;
    private final Path projectRoot;
    private final String projectName;
    private final List<Path> projectClassDirs;
    private final List<Path> dependencyJars;
    private final List<String> warnings;
    private final List<Path> tempDirs; // fat jar 解压临时目录，需清理

    public ProjectLayout(LayoutType type, Path projectRoot, String projectName,
                         List<Path> projectClassDirs, List<Path> dependencyJars,
                         List<String> warnings, List<Path> tempDirs) {
        this.type = type;
        this.projectRoot = projectRoot;
        this.projectName = projectName;
        this.projectClassDirs = projectClassDirs;
        this.dependencyJars = dependencyJars;
        this.warnings = warnings;
        this.tempDirs = tempDirs;
    }

    public LayoutType getType() { return type; }
    public Path getProjectRoot() { return projectRoot; }
    public String getProjectName() { return projectName; }
    public List<Path> getProjectClassDirs() { return projectClassDirs; }
    public List<Path> getDependencyJars() { return dependencyJars; }
    public List<String> getWarnings() { return warnings; }

    @Override
    public void close() throws IOException {
        for (Path dir : tempDirs) {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !java.nio.file.Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // 临时文件清理失败忽略
                }
            });
        }
    }
}
