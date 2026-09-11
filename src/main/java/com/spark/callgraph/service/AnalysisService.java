package com.spark.callgraph.service;

import com.spark.callgraph.engine.CallGraphBuilder;
import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.ClasspathResolver;
import com.spark.callgraph.engine.MavenRepoLocator;
import com.spark.callgraph.engine.ProjectLayout;
import com.spark.callgraph.engine.model.CallNode;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.service.dto.AnalyzeRequest;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.EntryRef;
import com.spark.callgraph.service.dto.ProjectInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 分析编排：布局识别 → 注册表构建（缓存复用）→ 入口解析 → 树构建 → 统计。
 */
@Service
public class AnalysisService {

    private static final int DEFAULT_MAX_DEPTH = 20;
    private static final int MAX_NODES = 50000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;

    /** 单槽缓存：同项目连续分析（树/Excel/类搜索）无需重建注册表 */
    private volatile CacheSlot cache;

    private static final class CacheSlot {
        final String path;
        final ProjectLayout layout;
        final ClassMetadataRegistry registry;
        final long ts;

        CacheSlot(String path, ProjectLayout layout, ClassMetadataRegistry registry) {
            this.path = path;
            this.layout = layout;
            this.registry = registry;
            this.ts = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------
    // 对外能力
    // ------------------------------------------------------------------

    public ProjectInfo projectInfo(String path) {
        try (ProjectLayout layout = ClasspathResolver.resolve(Paths.get(path), defaultMavenRepo())) {
            ProjectInfo info = new ProjectInfo();
            info.setPath(path);
            info.setName(layout.getProjectName());
            info.setLayoutType(layout.getType().name());
            info.setLayoutLabel(layout.getType().getLabel());
            info.setProjectClassCount(countClassFiles(layout));
            info.setDependencyJarCount(layout.getDependencyJars().size());
            info.setWarnings(new ArrayList<>(layout.getWarnings()));
            return info;
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "读取项目失败: " + e.getMessage());
        }
    }

    /** 注册表句柄：供入口扫描等服务复用缓存注册表 */
    public RegistryHandle registryFor(String projectPath) {
        CacheSlot slot = obtainRegistry(projectPath);
        return new RegistryHandle(slot.registry, slot.layout.getProjectName(),
                new ArrayList<>(slot.layout.getWarnings()));
    }

    /** 注册表 + 项目信息（只读快照） */
    public static final class RegistryHandle {
        private final ClassMetadataRegistry registry;
        private final String projectName;
        private final List<String> warnings;

        RegistryHandle(ClassMetadataRegistry registry, String projectName, List<String> warnings) {
            this.registry = registry;
            this.projectName = projectName;
            this.warnings = warnings;
        }

        public ClassMetadataRegistry getRegistry() { return registry; }
        public String getProjectName() { return projectName; }
        public List<String> getWarnings() { return warnings; }
    }

    public List<String> searchClasses(String path, String q) {
        if (q == null || q.trim().isEmpty()) return new ArrayList<>();
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String needle = q.trim();
        List<String> startsWith = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != SourceType.PROJECT) continue;
            String fqcn = ci.getInternalName().replace('/', '.');
            if (fqcn.startsWith(needle)) {
                startsWith.add(fqcn);
            } else if (fqcn.toLowerCase().contains(needle.toLowerCase())) {
                contains.add(fqcn);
            }
            if (startsWith.size() >= 20) break;
        }
        List<String> out = new ArrayList<>(startsWith);
        for (String c : contains) {
            if (out.size() >= 20) break;
            out.add(c);
        }
        return out;
    }

    public AnalysisResult analyze(AnalyzeRequest req) {
        long start = System.currentTimeMillis();
        validate(req);
        int maxDepth = req.getMaxDepth() == null
                ? DEFAULT_MAX_DEPTH
                : Math.max(1, Math.min(req.getMaxDepth(), 50));

        CacheSlot slot = obtainRegistry(req.getProjectPath());
        ClassMetadataRegistry registry = slot.registry;
        ProjectLayout layout = slot.layout;

        List<MethodKey> roots;
        String className = null;
        String methodName = null;
        if (req.getEntries() != null && !req.getEntries().isEmpty()) {
            // 多入口模式（来自入口扫描勾选）
            roots = new ArrayList<>();
            for (EntryRef ref : req.getEntries()) {
                String owner = resolveEntryClass(registry, ref.getClassName());
                ClassInfo info = registry.get(owner);
                roots.addAll(entryMethods(info, ref.getMethodName(), ref.getMethodDescriptor()));
            }
        } else {
            String internalOwner = resolveEntryClass(registry, req.getClassName());
            className = internalOwner.replace('/', '.');
            ClassInfo classInfo = registry.get(internalOwner);
            roots = entryMethods(classInfo, req.getMethodName());
            methodName = req.getMethodName() == null || req.getMethodName().trim().isEmpty()
                    ? null : req.getMethodName().trim();
        }

        CallGraphBuilder builder = new CallGraphBuilder(registry);
        List<CallNode> trees = builder.buildRoots(roots, maxDepth, MAX_NODES);

        AnalysisResult result = new AnalysisResult();
        result.setProjectPath(req.getProjectPath());
        result.setProjectName(layout.getProjectName());
        result.setLayoutType(layout.getType().name());
        result.setClassName(className);
        result.setMethodName(methodName);
        result.setRoots(trees);
        result.setWarnings(new ArrayList<>(layout.getWarnings()));
        fillStats(result, trees, System.currentTimeMillis() - start);
        return result;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void validate(AnalyzeRequest req) {
        if (req == null || req.getProjectPath() == null || req.getProjectPath().trim().isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不能为空");
        }
        boolean hasEntries = req.getEntries() != null && !req.getEntries().isEmpty();
        if (!hasEntries && (req.getClassName() == null || req.getClassName().trim().isEmpty())) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "类名不能为空");
        }
        Path p = Paths.get(req.getProjectPath().trim());
        if (!Files.exists(p)) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不存在: " + req.getProjectPath());
        }
    }

    private synchronized CacheSlot obtainRegistry(String projectPath) {
        String path = projectPath.trim();
        CacheSlot current = cache;
        if (current != null && current.path.equals(path)
                && System.currentTimeMillis() - current.ts < CACHE_TTL_MS) {
            return current;
        }
        if (current != null) {
            closeQuietly(current.layout);
        }
        try {
            ProjectLayout layout = ClasspathResolver.resolve(Paths.get(path), defaultMavenRepo());
            ClassMetadataRegistry.Builder builder = ClassMetadataRegistry.builder();
            for (Path dir : layout.getProjectClassDirs()) {
                builder.addClassesDir(dir, SourceType.PROJECT);
            }
            for (Path jar : layout.getDependencyJars()) {
                builder.addJar(jar, SourceType.DEPENDENCY);
            }
            ClassMetadataRegistry registry = builder.build();
            CacheSlot slot = new CacheSlot(path, layout, registry);
            cache = slot;
            return slot;
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "读取项目失败: " + e.getMessage());
        }
    }

    private String resolveEntryClass(ClassMetadataRegistry registry, String classNameInput) {
        String input = classNameInput.trim();
        String internal = input.replace('.', '/');
        if (registry.isKnown(internal)) return internal;
        Collection<String> bySimple = registry.classesBySimpleName(input);
        if (bySimple.isEmpty()) {
            throw new AnalysisException(HttpStatus.NOT_FOUND,
                    "类未找到（项目类中无匹配）: " + input);
        }
        if (bySimple.size() > 1) {
            throw new AnalysisException(HttpStatus.CONFLICT,
                    "存在 " + bySimple.size() + " 个同名类，请使用全限定名: "
                            + bySimple.stream().map(n -> n.replace('/', '.'))
                            .sorted().collect(Collectors.joining(", ")));
        }
        return bySimple.iterator().next();
    }

    private List<MethodKey> entryMethods(ClassInfo classInfo, String methodName) {
        return entryMethods(classInfo, methodName, null);
    }

    private List<MethodKey> entryMethods(ClassInfo classInfo, String methodName, String methodDescriptor) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return classInfo.methodKeys().stream()
                    .filter(m -> !isSyntheticOrBridge(classInfo, m))
                    .collect(Collectors.toList());
        }
        String name = methodName.trim();
        List<MethodKey> matching = classInfo.methodKeys().stream()
                .filter(m -> m.getName().equals(name))
                .collect(Collectors.toList());
        if (methodDescriptor != null && !methodDescriptor.trim().isEmpty()) {
            String desc = methodDescriptor.trim();
            List<MethodKey> exact = matching.stream()
                    .filter(m -> m.getDescriptor().equals(desc))
                    .collect(Collectors.toList());
            if (!exact.isEmpty()) matching = exact;
        }
        if (matching.isEmpty()) {
            String available = classInfo.methodKeys().stream()
                    .map(MethodKey::getName)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new AnalysisException(HttpStatus.NOT_FOUND,
                    "方法未找到: " + classInfo.getInternalName().replace('/', '.') + "." + name
                            + "。该类可用方法: " + available);
        }
        return matching;
    }

    private boolean isSyntheticOrBridge(ClassInfo ci, MethodKey m) {
        return (ci.methodAccess(m.getName(), m.getDescriptor()) & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0;
    }

    private void fillStats(AnalysisResult result, List<CallNode> trees, long durationMs) {
        AnalysisResult.Stats stats = result.getStats();
        stats.setEntryCount(trees.size());
        stats.setDurationMs(durationMs);
        for (CallNode root : trees) {
            count(root, stats);
        }
    }

    private void count(CallNode node, AnalysisResult.Stats stats) {
        stats.setTotalNodes(stats.getTotalNodes() + 1);
        switch (node.getSource()) {
            case PROJECT: stats.setProjectMethods(stats.getProjectMethods() + 1); break;
            case DEPENDENCY: stats.setDependencyMethods(stats.getDependencyMethods() + 1); break;
            default: stats.setExternalMethods(stats.getExternalMethods() + 1); break;
        }
        if (node.isTruncated()) stats.setTruncated(true);
        for (CallNode c : node.getChildren()) count(c, stats);
    }

    private int countClassFiles(ProjectLayout layout) {
        int count = 0;
        for (Path dir : layout.getProjectClassDirs()) {
            try (Stream<Path> walk = Files.walk(dir)) {
                count += (int) walk.filter(p -> p.toString().endsWith(".class")).count();
            } catch (IOException ignore) {
                // 计数失败忽略
            }
        }
        return count;
    }

    private Path defaultMavenRepo() {
        return MavenRepoLocator.detect();
    }

    /**
     * 页面默认演示值：默认分析本工具自身。
     * projectPath 取运行时 classpath 位置——fat jar 运行即分析该 jar（自带依赖，随处可用）；
     * classes 目录运行（IDE/mvn）则回退到项目根目录。
     */
    public AnalyzeRequest demoDefaults() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setClassName("com.spark.callgraph.service.AnalysisService");
        req.setMethodName("analyze");
        req.setProjectPath(selfLocation());
        return req;
    }

    private String selfLocation() {
        try {
            java.net.URL url = AnalysisService.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            String spec = url.toString();
            // Spring Boot fat jar：内嵌 classes 的 code source 形如
            // jar:file:/path/call-graph-analyzer.jar!/BOOT-INF/classes!/ → 取外层 jar 本身
            if (spec.startsWith("jar:file:")) {
                int idx = spec.indexOf("!/");
                if (idx > 0) {
                    Path jar = Paths.get(new java.net.URI(spec.substring(4, idx)));
                    if (Files.isRegularFile(jar)) return jar.toString();
                }
            }
            Path p = Paths.get(url.toURI());
            if (Files.isRegularFile(p)) {
                return p.toString(); // jar：直接作为 fat jar 分析
            }
            Path parent = p.getParent();
            return parent != null && parent.getParent() != null
                    ? parent.getParent().toString() // target/classes → 项目根
                    : p.toString();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    private void closeQuietly(ProjectLayout layout) {
        try {
            layout.close();
        } catch (IOException ignore) {
            // 清理失败忽略
        }
    }
}
