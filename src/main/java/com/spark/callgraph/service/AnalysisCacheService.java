package com.spark.callgraph.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spark.callgraph.config.CallgraphPaths;
import com.spark.callgraph.service.dto.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * 分析结果持久化缓存（JSON）。
 * 存于 D:\.callgraph\cache\<hash>.json，hash = 请求指纹。
 */
@Service
public class AnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);

    private final ObjectMapper mapper;

    public AnalysisCacheService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Optional<AnalysisResult> load(String projectPath, String className, String methodName,
                                          int maxDepth, int maxNodes, String freqSourceFilter) {
        try {
            String projFp = projectFingerprint(projectPath);
            Path file = cacheFile(projectPath, className, methodName, maxDepth, maxNodes, freqSourceFilter, projFp);
            if (!Files.isRegularFile(file)) {
                Path legacy = legacyCacheFile(projectPath, className, methodName, maxDepth, maxNodes, freqSourceFilter);
                if (!Files.isRegularFile(legacy)) return Optional.empty();
                file = legacy;
            }
            log.info("[缓存] 命中 {}", file.getFileName());
            return Optional.of(mapper.readValue(file.toFile(), AnalysisResult.class));
        } catch (Exception e) {
            log.warn("[缓存] 读取失败，将重新分析: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void save(String projectPath, String className, String methodName,
                     int maxDepth, int maxNodes, String freqSourceFilter,
                     AnalysisResult result) {
        try {
            String projFp = projectFingerprint(projectPath);
            Path file = cacheFile(projectPath, className, methodName, maxDepth, maxNodes, freqSourceFilter, projFp);
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), result);
            log.info("[缓存] 已写入 {}", file.getFileName());
        } catch (Exception e) {
            // 把 IOException 和 RuntimeException（比如 Jackson 序列化失败）都捕获并打印完整堆栈
            log.error("[缓存] 写入失败: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // 指纹 / 路径
    // ------------------------------------------------------------------

    /**
     * 缓存目录：强制放项目根目录下的 ".callgraph/cache/"。
     * 不可写时 warn 日志但仍然返回该路径，由调用方 catch IOException。
     * 缓存跟着项目走，项目搬去哪缓存就去哪，不再兜底全局。
     */
    private Path cacheDir(String projectPath) {
        if (projectPath != null && !projectPath.isEmpty()) {
            Path projectCache = Paths.get(projectPath, ".callgraph", "cache");
            try {
                Files.createDirectories(projectCache);
            } catch (IOException e) {
                log.warn("[缓存] 无法创建项目缓存目录 {}: {}", projectCache, e.getMessage());
            }
            return projectCache;
        }
        // projectPath 为空时（不应发生），兜底全局
        Path global = CallgraphPaths.getHome().resolve("cache");
        try { Files.createDirectories(global); } catch (IOException ignored) {}
        return global;
    }

    /** 某项目是否有任意分析缓存文件（只查项目内目录） */
    public boolean hasAnyCache(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return false;
        Path local = Paths.get(projectPath, ".callgraph", "cache");
        return hasJsonFile(local);
    }

    private static boolean hasJsonFile(Path dir) {
        try {
            if (!Files.isDirectory(dir)) return false;
            try (java.util.stream.Stream<Path> walk = Files.walk(dir, 2)) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName() != null
                                && p.getFileName().toString().endsWith(".json"))
                        .findFirst().isPresent();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private Path cacheFile(String projectPath, String className, String methodName,
                           int maxDepth, int maxNodes, String freqSourceFilter, String projFp) {
        String key = String.join("|",
                safe(className), safe(methodName),
                String.valueOf(maxDepth), String.valueOf(maxNodes),
                safe(freqSourceFilter), safe(projFp));
        String hash = sha1(key);
        String name = humanReadableName(className, methodName, hash);
        return cacheDir(projectPath).resolve(name);
    }

    /** 旧文件名兼容（hash-only），仅在 load 时 fallback 查找 */
    private Path legacyCacheFile(String projectPath, String className, String methodName,
                                 int maxDepth, int maxNodes, String freqSourceFilter) {
        String key = String.join("|",
                safe(className), safe(methodName),
                String.valueOf(maxDepth), String.valueOf(maxNodes),
                safe(freqSourceFilter));
        return cacheDir(projectPath).resolve(sha1(key) + ".json");
    }

    /** 生成直观文件名：SimpleClass#methodName_abcd1234.json
     *  - 类名取最后一段（SimpleClassName）
     *  - 方法名原样保留
     *  - 加 hash 前 8 位防同名冲突
     *  - 去掉所有非法文件名字符 */
    private static String humanReadableName(String className, String methodName, String hash) {
        String simpleName = safe(className);
        int dot = simpleName.lastIndexOf('.');
        if (dot >= 0) simpleName = simpleName.substring(dot + 1);
        // Windows 文件名非法字符: \ / : * ? " < > |
        String method = safe(methodName).replaceAll("[\\\\/:*?\"<>|]", "_");
        String cleanClass = simpleName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (method.isEmpty()) {
            return cleanClass + "_" + hash.substring(0, Math.min(8, hash.length())) + ".json";
        }
        return cleanClass + "#" + method + "_" + hash.substring(0, Math.min(8, hash.length())) + ".json";
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 项目指纹：用于判断源码/构建是否有变化。
     * 优先级：Git HEAD > pom.xml/build.gradle 时间 > target/classes 最新 .class 时间。
     */
    static String projectFingerprint(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return "";
        Path root = Paths.get(projectPath);

        // Git HEAD
        Path head = root.resolve(".git").resolve("HEAD");
        try {
            if (Files.isRegularFile(head)) {
                String ref = readFile(head);
                if (ref.startsWith("ref:")) {
                    Path refFile = root.resolve(".git").resolve(ref.substring(5).trim());
                    if (Files.isRegularFile(refFile)) {
                        return "git:" + readFile(refFile);
                    }
                } else {
                    return "git:" + ref.trim();
                }
            }
        } catch (IOException ignored) {}

        // pom.xml / build.gradle
        Path pom = root.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            try { return "pom:" + Files.getLastModifiedTime(pom).toMillis(); } catch (IOException ignored) {}
        }
        for (String name : new String[]{"build.gradle", "build.gradle.kts"}) {
            Path g = root.resolve(name);
            if (Files.isRegularFile(g)) {
                try { return "gradle:" + Files.getLastModifiedTime(g).toMillis(); } catch (IOException ignored) {}
            }
        }

        // 最新 .class 文件
        long newest = newestClassTime(root);
        return newest > 0 ? "class:" + newest : "";
    }

    private static String readFile(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private static long newestClassTime(Path root) {
        long newest = 0L;
        Path[] dirs = {
                root.resolve("target").resolve("classes"),
                root.resolve("bin").resolve("classes"),
                root.resolve("out").resolve("production"),
        };
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try {
                java.util.List<Path> classFiles = new java.util.ArrayList<>();
                collectClassFiles(dir, classFiles);
                for (Path p : classFiles) {
                    try {
                        long t = Files.getLastModifiedTime(p).toMillis();
                        if (t > newest) newest = t;
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }
        return newest;
    }

    private static void collectClassFiles(Path dir, java.util.List<Path> out) throws IOException {
        java.util.List<Path> list = Files.list(dir).collect(java.util.stream.Collectors.toList());
        for (Path p : list) {
            if (Files.isDirectory(p)) collectClassFiles(p, out);
            else if (p.toString().endsWith(".class")) out.add(p);
        }
    }
}
