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
import java.util.Comparator;
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
        List<String> packages = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != SourceType.PROJECT) continue;
            String fqcn = ci.getInternalName().replace('/', '.');
            if (fqcn.startsWith(needle)) {
                startsWith.add(fqcn);
                // 同时给出包名建议：手动添加处可直接填包名，列出该包本层的方法
                String pkg = fqcn.lastIndexOf('.') > 0 ? fqcn.substring(0, fqcn.lastIndexOf('.')) : "";
                if (pkg.startsWith(needle) && !packages.contains(pkg)) packages.add(pkg);
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
        // 包名建议排在类名之后（调用方按 $[0] 取类名的既有契约保持不变）
        int pkgCap = Math.min(10, packages.size());
        for (int i = 0; i < pkgCap && out.size() < 30; i++) out.add(packages.get(i));
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

    // ------------------------------------------------------------------
    // 「类名 / 包名 → 方法列表」：手动添加入口的枚举能力
    // ------------------------------------------------------------------

    /** 单次枚举的返回上限：防止一个超大包把弹窗列表撑爆 */
    private static final int METHODS_UNDER_MAX = 500;

    /** 单条候选方法（全限定类名 + 方法名 + 描述符） */
    public static final class MethodRef {
        private final String className;
        private final String methodName;
        private final String descriptor;

        MethodRef(String className, String methodName, String descriptor) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public String getDescriptor() { return descriptor; }
    }

    /** 「类名 / 包名」解析结果 */
    public static final class MethodQuery {
        /** CLASS = 命中一个类；PACKAGE = 按包名处理（可能本层没有类）；NONE = 既不是类也不是包 */
        private final String mode;
        private final String resolvedName;   // 解析出的全限定类名 / 包名
        private final List<MethodRef> methods;
        private final boolean truncated;     // 命中数超过上限，只返回了前 N 个

        MethodQuery(String mode, String resolvedName, List<MethodRef> methods, boolean truncated) {
            this.mode = mode;
            this.resolvedName = resolvedName;
            this.methods = methods;
            this.truncated = truncated;
        }

        public String getMode() { return mode; }
        public String getResolvedName() { return resolvedName; }
        public List<MethodRef> getMethods() { return methods; }
        public boolean isTruncated() { return truncated; }
    }

    /**
     * 解析用户填的"类名或包名"，列出其下面的全部方法（供手动添加入口挑选）。
     * <p>
     * 解析顺序：全限定类名精确命中 → 唯一简单类名命中（与 verifyEntry 同语义）→ 按包名处理。
     * 包名**只列本层**，不递归子包（产品约定）。构造器 / 静态初始化 / 编译器合成方法（含 $）不作为候选。
     */
    public MethodQuery methodsUnder(String path, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new MethodQuery("NONE", "", new ArrayList<>(), false);

        ClassMetadataRegistry registry = obtainRegistry(path).registry;
        String internal = q.replace('.', '/');

        ClassInfo ci = registry.get(internal);
        if (ci == null) {
            String simple = internal.substring(internal.lastIndexOf('/') + 1);
            Collection<String> bySimple = registry.classesBySimpleName(simple);
            if (bySimple != null && bySimple.size() == 1) ci = registry.get(bySimple.iterator().next());
        }
        if (ci != null) {
            List<MethodRef> out = new ArrayList<>();
            boolean truncated = false;
            String fqcn = ci.getInternalName().replace('/', '.');
            for (MethodKey mk : ci.methodKeys()) {
                if (isNotCandidate(mk.getName())) continue;
                if (out.size() >= METHODS_UNDER_MAX) { truncated = true; break; }
                out.add(new MethodRef(fqcn, mk.getName(), mk.getDescriptor()));
            }
            out.sort(Comparator.comparing(MethodRef::getMethodName)
                    .thenComparing(MethodRef::getDescriptor));
            return new MethodQuery("CLASS", fqcn, out, truncated);
        }

        // 包名：只取本层（internalName 以 pkg/ 开头且其后不再有 '/'）
        String prefix = internal + "/";
        List<MethodRef> out = new ArrayList<>();
        boolean truncated = false;
        boolean packageExists = false;
        for (ClassInfo c : registry.allClasses()) {
            if (c.getSource() != SourceType.PROJECT) continue;
            String n = c.getInternalName();
            if (!n.startsWith(prefix)) continue;
            packageExists = true;
            if (n.indexOf('/', prefix.length()) >= 0) continue;   // 子包，跳过
            String fqcn = n.replace('/', '.');
            for (MethodKey mk : c.methodKeys()) {
                if (isNotCandidate(mk.getName())) continue;
                if (out.size() >= METHODS_UNDER_MAX) { truncated = true; break; }
                out.add(new MethodRef(fqcn, mk.getName(), mk.getDescriptor()));
            }
        }
        if (!packageExists) return new MethodQuery("NONE", q, out, false);
        out.sort(Comparator.comparing(MethodRef::getClassName)
                .thenComparing(MethodRef::getMethodName)
                .thenComparing(MethodRef::getDescriptor));
        return new MethodQuery("PACKAGE", internal.replace('/', '.'), out, truncated);
    }

    /** 构造器 / 静态初始化块 / 编译器合成方法（lambda$、access$、$default 等）不作为候选入口 */
    private static boolean isNotCandidate(String methodName) {
        return methodName == null
                || "<init>".equals(methodName)
                || "<clinit>".equals(methodName)
                || methodName.indexOf('$') >= 0;
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