package com.spark.projectanalysis.service;

import com.spark.projectanalysis.engine.ClassMetadataRegistry;
import com.spark.projectanalysis.engine.ClasspathResolver;
import com.spark.projectanalysis.engine.MavenRepoLocator;
import com.spark.projectanalysis.engine.ProjectLayout;
import com.spark.projectanalysis.engine.model.ClassInfo;
import com.spark.projectanalysis.engine.model.MethodKey;
import com.spark.projectanalysis.engine.model.SourceType;
import com.spark.projectanalysis.service.dto.EntryRef;
import com.spark.projectanalysis.service.dto.EntryVerifyResult;
import com.spark.projectanalysis.service.dto.ProjectInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 类元数据管理：注册表构建（缓存复用）+ 类/方法搜索 + 项目信息查询。
 */
@Service
public class ClassMetadataService {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;

    /** 单槽缓存：同项目连续分析（树/Excel/类搜索）无需重建注册表 */
    private volatile CacheSlot cache;

    static final class CacheSlot {
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
        return registryFor(projectPath, null);
    }

    /** 进度回调：progressCb.accept(phase, done, total, desc)
     *  phase 0=解析classpath, 1=开始索引, 2=索引中（会被 Builder 多次触发，done/total 为 entry 级）, 3=完成 */
    public RegistryHandle registryFor(String projectPath, ProgressCallback progressCb) {
        CacheSlot slot = obtainRegistry(projectPath, progressCb);
        return new RegistryHandle(slot.registry, slot.layout.getProjectName(),
                slot.layout.getType().name(), new ArrayList<>(slot.layout.getWarnings()));
    }

    /** 自定义进度回调接口 */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(int phase, int done, int total, String desc);
    }

    /** 注册表 + 项目信息（只读快照） */
    public static final class RegistryHandle {
        private final ClassMetadataRegistry registry;
        private final String projectName;
        private final String layoutType;
        private final List<String> warnings;

        RegistryHandle(ClassMetadataRegistry registry, String projectName,
                       String layoutType, List<String> warnings) {
            this.registry = registry;
            this.projectName = projectName;
            this.layoutType = layoutType;
            this.warnings = warnings;
        }

        public ClassMetadataRegistry getRegistry() { return registry; }
        public String getProjectName() { return projectName; }
        public String getLayoutType() { return layoutType; }
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

    /** 返回某个类的所有方法（name + descriptor），供前端下拉选 */
    public List<Map<String, String>> getMethods(String path, String fullClassName) {
        List<Map<String, String>> out = new ArrayList<>();
        if (fullClassName == null || fullClassName.trim().isEmpty()) return out;
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String internalName = fullClassName.trim().replace('.', '/');
        ClassInfo ci = registry.get(internalName);
        if (ci == null) return out;
        for (var mk : ci.methodKeys()) {
            Map<String, String> m = new HashMap<>();
            m.put("name", mk.getName());
            m.put("descriptor", mk.getDescriptor());
            out.add(m);
        }
        out.sort((a, b) -> a.get("name").compareTo(b.get("name")));
        return out;
    }

    /** 验证 className + methodName + descriptor 是否在项目中真实存在 */
    public EntryVerifyResult verifyEntry(String path, String className, String methodName, String descriptor) {
        EntryVerifyResult resp = new EntryVerifyResult();
        if (className == null || className.trim().isEmpty()) {
            resp.setOk(false); resp.setReason("类名不能为空"); return resp;
        }
        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String internalName = className.trim().replace('.', '/');
        ClassInfo ci = registry.get(internalName);
        if (ci == null) {
            String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
            var candidates = registry.classesBySimpleName(simple);
            if (candidates == null || candidates.isEmpty()) {
                resp.setOk(false); resp.setReason("项目中未找到类: " + className); return resp;
            }
            if (candidates.size() == 1) {
                ci = registry.get(candidates.iterator().next());
            } else {
                resp.setOk(false);
                resp.setReason("找到 " + candidates.size() + " 个同名类，请填全限定名");
                List<String> fullNames = new ArrayList<>();
                for (String cn : candidates) fullNames.add(cn.replace('/', '.'));
                resp.setCandidates(fullNames);
                return resp;
            }
        }
        if (methodName == null || methodName.trim().isEmpty()) {
            resp.setOk(true); resp.setReason("类存在: " + ci.getInternalName().replace('/', '.'));
            return resp;
        }
        String mname = methodName.trim();
        String desc = descriptor == null ? "" : descriptor.trim();
        if (desc.isEmpty()) {
            boolean found = false;
            List<String> descs = new ArrayList<>();
            for (var mk : ci.methodKeys()) {
                if (mk.getName().equals(mname)) { found = true; descs.add(mk.getDescriptor()); }
            }
            if (!found) {
                resp.setOk(false);
                resp.setReason("类存在但方法 " + mname + " 不存在");
                resp.setAvailable(ci.methodKeys().stream().map(mk -> mk.getName()).distinct().collect(Collectors.toList()));
                return resp;
            }
            if (descs.size() == 1) {
                resp.setOk(true);
                resp.setDescriptor(descs.get(0));
                resp.setReason("✓ 已匹配唯一重载，建议 descriptor: " + descs.get(0));
            } else {
                resp.setOk(true);
                resp.setMultipleOverloads(descs);
                resp.setReason("✓ 方法存在但有 " + descs.size() + " 个重载，建议指定 descriptor 精确匹配");
            }
            return resp;
        }
        if (ci.hasOwnMethod(mname, desc)) {
            resp.setOk(true);
            resp.setReason("✓ 完整匹配: " + className + "#" + mname + desc);
        } else {
            resp.setOk(false);
            resp.setReason("类存在，但未找到方法 " + mname + desc);
        }
        return resp;
    }

    /** 解析单个交易入口引用为根方法列表（供批量分析服务复用） */
    public List<MethodKey> resolveEntryRoots(ClassMetadataRegistry registry, EntryRef ref) {
        String owner = resolveEntryClass(registry, ref.getClassName());
        ClassInfo info = registry.get(owner);
        return entryMethods(info, ref.getMethodName(), ref.getMethodDescriptor());
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    synchronized CacheSlot obtainRegistry(String projectPath) {
        return obtainRegistry(projectPath, null);
    }

    synchronized CacheSlot obtainRegistry(String projectPath, ProgressCallback progressCb) {
        String path = projectPath.trim();
        CacheSlot current = cache;
        if (current != null && current.path.equals(path)
                && System.currentTimeMillis() - current.ts < CACHE_TTL_MS) {
            if (progressCb != null) progressCb.accept(3, 1, 1, "注册表命中缓存");
            return current;
        }
        if (current != null) {
            closeQuietly(current.layout);
        }
        try {
            if (progressCb != null) progressCb.accept(0, 0, 3, "解析项目 classpath...");
            ProjectLayout layout = ClasspathResolver.resolve(Paths.get(path), defaultMavenRepo());

            int total = layout.getProjectClassDirs().size() + layout.getDependencyJars().size();
            if (progressCb != null) progressCb.accept(1, 0, total, "开始索引 " + total + " 个条目...");

            ClassMetadataRegistry.Builder builder = ClassMetadataRegistry.builder();
            for (Path dir : layout.getProjectClassDirs()) {
                builder.addClassesDir(dir, SourceType.PROJECT);
            }
            for (Path jar : layout.getDependencyJars()) {
                builder.addJar(jar, SourceType.DEPENDENCY);
            }
            builder.withProgress((done, desc) -> {
                if (progressCb != null) progressCb.accept(2, done, total, desc);
            });
            ClassMetadataRegistry registry = builder.build();

            if (progressCb != null) progressCb.accept(3, total, total, "注册表构建完成");

            CacheSlot slot = new CacheSlot(path, layout, registry);
            cache = slot;
            return slot;
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR, "读取项目失败: " + e.getMessage());
        }
    }

    String resolveEntryClass(ClassMetadataRegistry registry, String classNameInput) {
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

    List<MethodKey> entryMethods(ClassInfo classInfo, String methodName) {
        return entryMethods(classInfo, methodName, null);
    }

    List<MethodKey> entryMethods(ClassInfo classInfo, String methodName, String methodDescriptor) {
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

    private int countClassFiles(ProjectLayout layout) {
        int count = 0;
        for (Path dir : layout.getProjectClassDirs()) {
            try (Stream<Path> walk = Files.walk(dir)) {
                count += (int) walk.filter(p -> p.toString().endsWith(".class")).count();
            } catch (IOException ignore) {
            }
        }
        return count;
    }

    private Path defaultMavenRepo() {
        return MavenRepoLocator.detect();
    }

    private void closeQuietly(ProjectLayout layout) {
        try {
            layout.close();
        } catch (IOException ignore) {
        }
    }
}