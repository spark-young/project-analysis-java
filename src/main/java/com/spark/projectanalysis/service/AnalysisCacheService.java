package com.spark.projectanalysis.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spark.projectanalysis.config.CallgraphPaths;
import com.spark.projectanalysis.service.dto.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.spark.projectanalysis.service.dto.CacheFileInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 分析结果持久化缓存（JSON）。
 * 存于项目根目录 ".callgraph/cache/" 下，入口级文件名 = SimpleClass#method_<hash>.json，
 * hash 只由 入口 + 分析配置 决定，不含项目指纹 —— 同一入口重分析覆盖同名文件，不堆积旧版本。
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
            Path file = cacheDir(projectPath)
                    .resolve(cacheName(className, methodName, maxDepth, maxNodes, freqSourceFilter));
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
            Path file = cacheDir(projectPath)
                    .resolve(cacheName(className, methodName, maxDepth, maxNodes, freqSourceFilter));
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
        Path local = cacheDir(projectPath);
        return hasJsonFile(local);
    }

    // ==================================================================
    // 简化版：Step2 清单驱动的单份缓存（固定文件名 + 清单签名）
    // ==================================================================

    public static final String SINGLE_CACHE_FILE = "analysis_result.json";

    /** 批量完成后清理本批未覆盖的 per-entry JSON（旧指纹版本 / 已移除入口），只保留本次覆盖到的名字，避免堆积。
     *  只清理"入口级 hash 文件"，不动 analysis_result.json 等固定主缓存。 */
    public void pruneBatchEntries(String projectPath, Set<String> keepNames) {
        if (projectPath == null || projectPath.isEmpty() || keepNames == null) return;
        Path dir = cacheDir(projectPath);
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(p -> Files.isRegularFile(p))
                    .filter(p -> isPerEntryFile(p.getFileName().toString()))
                    .filter(p -> !keepNames.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("[缓存] 清理旧入口文件失败 {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /** per-entry 文件判定：以 8 位十六进制哈希结尾的 .json（区别于 analysis_result.json 等固定文件） */
    private boolean isPerEntryFile(String name) {
        if (name == null || name.equals(SINGLE_CACHE_FILE) || !name.endsWith(".json")) return false;
        int dot = name.lastIndexOf('.');
        int under = name.lastIndexOf('_');
        if (under < 0 || dot <= under) return false;
        String hex = name.substring(under + 1, dot);
        return hex.matches("[0-9a-f]{8}");
    }

    /** 计算某次分析对应的缓存文件名（与 save/load 完全一致），供批量分析汇总索引用。
     *  只含 入口 + 分析配置，不含项目指纹 —— 同一入口用同一配置重分析时覆盖同名文件，
     *  避免代码变更后旧 JSON 不断堆积。 */
    public String fileNameOf(String className, String methodName,
                             int maxDepth, int maxNodes, String freqSourceFilter) {
        return cacheName(className, methodName, maxDepth, maxNodes, freqSourceFilter);
    }

    /** 计算 EntryList 的签名（confirmed 所有 key 排序拼接后 MD5） */
    public String entryListFingerprint(List<com.spark.projectanalysis.service.dto.EntryList.EntryItem> confirmed) {
        if (confirmed == null || confirmed.isEmpty()) return "";
        try {
            java.util.TreeSet<String> keys = new java.util.TreeSet<>();
            for (var e : confirmed) keys.add(e.key());
            String sorted = String.join("|", keys);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sorted.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(confirmed.size());
        }
    }

    /** 保存单份缓存（覆盖写），同时存清单签名。
     *  result 已经是 JSON 兼容结构（来自前端回传的完整结果），直接落盘，避免二次反序列化丢失。
     */
    public void saveSingle(String projectPath, Object result, String entryListHash, int entryCount) {
        try {
            Path dir = cacheDir(projectPath);
            Files.createDirectories(dir);
            Path file = dir.resolve(SINGLE_CACHE_FILE);

            java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("entryListHash", entryListHash == null ? "" : entryListHash);
            wrapper.put("entryCount", entryCount);
            wrapper.put("analyzedAt", System.currentTimeMillis());
            wrapper.put("result", result);

            // 直接写（不原子，简单可靠）
            mapper.writeValue(file.toFile(), wrapper);
            log.info("[缓存] 单份已写入 entries={}, hash={}, file={}", entryCount, entryListHash, file);
        } catch (Exception e) {
            log.error("[缓存] 单份写入失败: {}", e.getMessage(), e);
        }
    }

    /** 单份缓存最大体积（MB）：超过则视为旧版损坏/巨量格式，不再读取（避免进入项目卡死）。
     *  批量分析现在只把轻量索引写进单份缓存，正常只有几 KB。 */
    private static final long SINGLE_CACHE_MAX_BYTES = 30L * 1024 * 1024;

    /** 加载单份缓存。返回 null 表示不存在（或体积过大被忽略）；返回 Map 含 result + meta */
    public java.util.Map<String, Object> loadSingle(String projectPath) {
        try {
            Path file = cacheDir(projectPath).resolve(SINGLE_CACHE_FILE);
            if (!Files.isRegularFile(file)) return null;
            long size = Files.size(file);
            if (size > SINGLE_CACHE_MAX_BYTES) {
                log.warn("[缓存] 单份缓存体积过大({}MB)已忽略读取，请重新执行批量分析生成新格式: {}",
                        size / 1024 / 1024, file);
                return null;
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> wrapper = mapper.readValue(file.toFile(), java.util.Map.class);
            return wrapper;
        } catch (Exception e) {
            log.warn("[缓存] 单份读取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 列出项目内所有缓存文件（按最后修改时间降序，最新的排最前）。
     * 用于前端下拉切换不同批次的分析结果。
     */
    public List<CacheFileInfo> listAll(String projectPath) {
        List<CacheFileInfo> result = new ArrayList<>();
        if (projectPath == null || projectPath.isEmpty()) return result;
        Path dir = Paths.get(projectPath, ".callgraph", "cache");
        if (!Files.isDirectory(dir)) return result;
        try (java.util.stream.Stream<Path> walk = Files.walk(dir, 2)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName() != null
                        && p.getFileName().toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        long size = Files.size(p);
                        long mtime = Files.getLastModifiedTime(p).toMillis();
                        result.add(new CacheFileInfo(name, size, mtime));
                    } catch (IOException ignored) {}
                });
        } catch (Exception e) {
            log.warn("[缓存] listAll 失败: {}", e.getMessage());
        }
        result.sort(Comparator.comparingLong((CacheFileInfo c) -> c.lastModifiedMs).reversed());
        return result;
    }

    /** 加载项目内最新（修改时间最大）的缓存文件 */
    public Optional<AnalysisResult> loadLatest(String projectPath) {
        List<CacheFileInfo> list = listAll(projectPath);
        if (list.isEmpty()) return Optional.empty();
        return loadByFileName(projectPath, list.get(0).fileName);
    }

    /** 按具体文件名加载（用于下拉切换） */
    public Optional<AnalysisResult> loadByFileName(String projectPath, String fileName) {
        Path file;
        try {
            file = cacheDir(projectPath).resolve(fileName);
            if (!Files.isRegularFile(file)) {
                log.warn("[缓存] 文件不存在: {}", file);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("[缓存] 按名称加载失败 {}: {}", fileName, e.getMessage());
            return Optional.empty();
        }
        return loadCached(file, fileName);
    }

    // ------------------------------------------------------------------
    // 结果内存缓存：反复展开/刷新入口时避免重复读盘 + 反序列化大 JSON
    // ------------------------------------------------------------------

    /** 缓存条目上限（按最近使用淘汰）；单文件超过体积上限则不缓存，避免占用过多内存 */
    private static final int RESULT_CACHE_MAX = 12;
    private static final long RESULT_CACHE_MAX_BYTES = 8L * 1024 * 1024;

    private static final class CachedResult {
        final long mtime;
        final long size;
        final AnalysisResult result;

        CachedResult(long mtime, long size, AnalysisResult result) {
            this.mtime = mtime;
            this.size = size;
            this.result = result;
        }
    }

    private final java.util.Map<String, CachedResult> resultCache =
            new java.util.LinkedHashMap<String, CachedResult>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedResult> eldest) {
                    return size() > RESULT_CACHE_MAX;
                }
            };

    /** 带内存缓存的读取：文件 mtime/size 未变则直接返回内存结果 */
    private Optional<AnalysisResult> loadCached(Path file, String fileName) {
        String key = file.toAbsolutePath().toString();
        long mtime = -1L, size = -1L;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException ignored) {
        }
        synchronized (resultCache) {
            CachedResult c = resultCache.get(key);
            if (c != null && c.mtime == mtime && c.size == size) {
                return Optional.of(c.result);
            }
        }
        try {
            AnalysisResult parsed = mapper.readValue(file.toFile(), AnalysisResult.class);
            if (size >= 0 && size <= RESULT_CACHE_MAX_BYTES) {
                synchronized (resultCache) {
                    resultCache.put(key, new CachedResult(mtime, size, parsed));
                }
            }
            log.info("[缓存] 按名称加载 {}", fileName);
            return Optional.of(parsed);
        } catch (Exception e) {
            log.warn("[缓存] 按名称加载失败 {}: {}", fileName, e.getMessage());
            return Optional.empty();
        }
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

    /** 入口级缓存文件名：入口 + 分析配置决定，不含项目指纹（保证重分析覆盖同名文件） */
    private static String cacheName(String className, String methodName,
                                    int maxDepth, int maxNodes, String freqSourceFilter) {
        String key = String.join("|",
                safe(className), safe(methodName),
                String.valueOf(maxDepth), String.valueOf(maxNodes),
                safe(freqSourceFilter));
        return humanReadableName(className, methodName, sha1(key));
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
     * 注意：仅作源码变更判断用，不参与缓存文件名计算。
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
