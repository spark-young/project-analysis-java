package com.spark.callgraph.engine;

import com.spark.callgraph.engine.model.AnnotationInfo;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.RawCall;
import com.spark.callgraph.engine.model.Resolution;
import com.spark.callgraph.engine.model.SourceType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 类元数据注册表。
 * Phase A：classpath 上全部 .class 以 SKIP_CODE 快速索引类头+方法表；
 * Phase B：按需解析方法体（缓存）。
 */
public final class ClassMetadataRegistry {

    private static final int ASM = Opcodes.ASM9;

    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    /** location -> internalName -> (name+desc -> RawCalls) */
    private final Map<String, Map<String, Map<String, List<RawCall>>>> bodyCache = new ConcurrentHashMap<>();
    /** 接口/抽象分派结果缓存 */
    private final Map<String, List<MethodKey>> implCache = new ConcurrentHashMap<>();

    private ClassMetadataRegistry() {}

    public static Builder builder() { return new Builder(); }
    static boolean isJdkInternal(String internalName) {
        return internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || internalName.startsWith("com/sun/") || internalName.startsWith("com/oracle/")
                || internalName.startsWith("org/w3c/dom/") || internalName.startsWith("org/xml/sax/")
                || internalName.startsWith("org/omg/");
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public ClassInfo get(String internalName) { return classes.get(internalName); }

    public boolean isKnown(String internalName) { return classes.containsKey(internalName); }

    public Collection<ClassInfo> allClasses() { return classes.values(); }

    public Collection<String> classesBySimpleName(String simpleName) {
        List<String> out = new ArrayList<>();
        for (String name : classes.keySet()) {
            int i = name.lastIndexOf('/');
            String sn = i < 0 ? name : name.substring(i + 1);
            if (sn.equals(simpleName)) out.add(name);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 调用点解析
    // ------------------------------------------------------------------

    /**
     * 解析一条调用指令的目标方法：
     * 沿接收者类型层次上溯（super 优先、接口次之）找具体声明；
     * 接口/抽象声明 -> 枚举注册表中全部具体实现；JDK -> 排除；未知类 -> EXTERNAL。
     */
    public Resolution resolve(String owner, String name, String descriptor) {
        if (isJdkInternal(owner)) return Resolution.jdk();
        ClassInfo ownerInfo = classes.get(owner);
        if (ownerInfo == null) return Resolution.external(MethodKey.of(owner, name, descriptor));

        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        boolean sawUnknownNonJdk = false;
        String abstractDeclaredOwner = null;

        queue.add(owner);
        while (!queue.isEmpty()) {
            String cls = queue.pollFirst();
            if (cls == null || !seen.add(cls)) continue;
            ClassInfo ci = classes.get(cls);
            if (ci == null) {
                if (isJdkInternal(cls)) continue; // java/lang/Object 等正常终点
                sawUnknownNonJdk = true;
                continue;
            }
            if (ci.hasOwnMethod(name, descriptor)) {
                if (ci.isMethodConcrete(name, descriptor)) {
                    return Resolution.single(MethodKey.of(cls, name, descriptor));
                }
                if (abstractDeclaredOwner == null) abstractDeclaredOwner = cls;
                continue;
            }
            if (ci.getSuperName() != null) queue.addFirst(ci.getSuperName());
            for (String itf : ci.getInterfaces()) queue.addLast(itf);
        }

        // 接口接收者优先枚举实现（含 default 兜底在 dispatchTargets 内）
        if (ownerInfo.isInterface()) {
            List<MethodKey> impls = dispatchTargets(owner, name, descriptor);
            if (!impls.isEmpty()) return Resolution.multi(impls);
        }
        if (abstractDeclaredOwner != null) {
            List<MethodKey> impls = dispatchTargets(abstractDeclaredOwner, name, descriptor);
            if (!impls.isEmpty()) return Resolution.multi(impls);
            return Resolution.external(MethodKey.of(owner, name, descriptor));
        }
        return sawUnknownNonJdk
                ? Resolution.external(MethodKey.of(owner, name, descriptor))
                : Resolution.jdk();
    }

    /** 枚举声明类型的全部具体实现（类本身 default 方法兜底） */
    private List<MethodKey> dispatchTargets(String declaredOwner, String name, String descriptor) {
        String cacheKey = declaredOwner + '#' + name + descriptor;
        return implCache.computeIfAbsent(cacheKey, k -> {
            LinkedHashSet<MethodKey> out = new LinkedHashSet<>();
            for (ClassInfo ci : classes.values()) {
                if (ci.isInterface() || ci.isAbstract()) continue;
                if (!ci.hasOwnMethod(name, descriptor) || !ci.isMethodConcrete(name, descriptor)) continue;
                if (isSubtypeOf(ci.getInternalName(), declaredOwner)) {
                    out.add(MethodKey.of(ci.getInternalName(), name, descriptor));
                }
            }
            if (out.isEmpty()) {
                ClassInfo di = classes.get(declaredOwner);
                if (di != null && di.hasOwnMethod(name, descriptor) && di.isMethodConcrete(name, descriptor)) {
                    out.add(MethodKey.of(declaredOwner, name, descriptor)); // default 方法兜底
                }
            }
            return new ArrayList<>(out);
        });
    }

    /** cls 是否为 target 的子类型（含超类与全部接口，传递闭包） */
    public boolean isSubtypeOf(String cls, String target) {
        if (cls.equals(target)) return true;
        ArrayDeque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        stack.push(cls);
        while (!stack.isEmpty()) {
            String c = stack.pop();
            if (c == null || !visited.add(c)) continue;
            if (c.equals(target)) return true;
            ClassInfo ci = classes.get(c);
            if (ci == null) continue;
            if (ci.getSuperName() != null) stack.push(ci.getSuperName());
            for (String itf : ci.getInterfaces()) stack.push(itf);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Phase B：按需解析方法体
    // ------------------------------------------------------------------

    public List<RawCall> callsOf(MethodKey method) {
        ClassInfo ci = classes.get(method.getOwner());
        if (ci == null) return new ArrayList<>();
        try {
            Map<String, List<RawCall>> bodies = bodyCache
                    .computeIfAbsent(ci.getLocation(), loc -> new ConcurrentHashMap<>())
                    .computeIfAbsent(ci.getInternalName(), n -> {
                        try {
                            return MethodCallExtractor.extract(java.nio.file.Paths.get(ci.getLocation()), n);
                        } catch (IOException e) {
                            return new HashMap<String, List<RawCall>>();
                        }
                    });
            List<RawCall> calls = bodies.get(method.getName() + method.getDescriptor());
            return calls != null ? calls : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
    // 构建期索引
    // ------------------------------------------------------------------

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private java.util.function.BiConsumer<Integer, String> progressCb;

        /** 设置进度回调：(已处理数, 描述文字) */
        public Builder withProgress(java.util.function.BiConsumer<Integer, String> cb) {
            this.progressCb = cb;
            return this;
        }

        public Builder addClassesDir(Path dir, SourceType source) {
            entries.add(new Entry(dir, source));
            return this;
        }

        public Builder addJar(Path jar, SourceType source) {
            entries.add(new Entry(jar, source));
            return this;
        }

        public ClassMetadataRegistry build() throws IOException {
            ClassMetadataRegistry registry = new ClassMetadataRegistry();
            int total = entries.size();
            int done = 0;
            for (Entry entry : entries) {
                if (Files.isDirectory(entry.path)) {
                    indexDirectory(registry, entry.path, entry.source);
                } else if (Files.exists(entry.path)) {
                    indexJar(registry, entry.path, entry.source);
                }
                done++;
                if (progressCb != null) {
                    String name = entry.path.getFileName() == null
                            ? entry.path.toString()
                            : entry.path.getFileName().toString();
                    progressCb.accept(done, "正在索引 " + name + "（" + done + "/" + total + "）");
                }
            }
            return registry;
        }

        private void indexDirectory(ClassMetadataRegistry registry, Path dir, SourceType source) throws IOException {
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                    try {
                        String relative = dir.relativize(p).toString().replace('\\', '/');
                        String internalName = relative.substring(0, relative.length() - ".class".length());
                        registry.indexClass(Files.readAllBytes(p), internalName, source, dir.toString());
                    } catch (IOException ignore) {
                        // 单个类损坏不影响整体
                    }
                });
            }
        }

        private void indexJar(ClassMetadataRegistry registry, Path jar, SourceType source) throws IOException {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                java.util.Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (!n.endsWith(".class")) continue;
                    String internalName = n.substring(0, n.length() - ".class".length());
                    try (InputStream in = zip.getInputStream(e)) {
                        byte[] buf = readAll(in);
                        registry.indexClass(buf, internalName, source, jar.toString());
                    }
                }
            }
        }

        private static byte[] readAll(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) bos.write(buf, 0, len);
            return bos.toByteArray();
        }

        private static final class Entry {
            final Path path;
            final SourceType source;

            Entry(Path path, SourceType source) {
                this.path = path;
                this.source = source;
            }
        }
    }

    private synchronized void indexClass(byte[] bytes, String internalName, SourceType source, String location) {
        if (internalName.equals("module-info") || internalName.endsWith("package-info")) return;
        if (isJdkInternal(internalName)) return;
        if (classes.containsKey(internalName)) return; // 项目类优先，重复注册忽略
        try {
            ClassReader reader = new ClassReader(bytes);
            final String[] superRef = new String[1];
            final int[] accessRef = new int[1];
            final List<String> interfaces = new ArrayList<>();
            final Map<String, Integer> methods = new LinkedHashMap<>();
            final List<AnnotationInfo.Collector> classAnns = new ArrayList<>();
            final Map<String, List<AnnotationInfo>> methodAnns = new LinkedHashMap<>();
            reader.accept(new ClassVisitor(ASM) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] itfs) {
                    superRef[0] = superName;
                    accessRef[0] = access;
                    if (itfs != null) interfaces.addAll(java.util.Arrays.asList(itfs));
                }

                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    AnnotationInfo.Collector collector = new AnnotationInfo.Collector(ASM, descriptor);
                    classAnns.add(collector);
                    return collector;
                }

                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                    String signature, String[] exceptions) {
                    methods.put(name + descriptor, access);
                    final List<AnnotationInfo.Collector> collectors = new ArrayList<>();
                    return new org.objectweb.asm.MethodVisitor(ASM) {
                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            AnnotationInfo.Collector c = new AnnotationInfo.Collector(ASM, desc);
                            collectors.add(c);
                            return c;
                        }

                        @Override
                        public void visitEnd() {
                            if (!collectors.isEmpty()) {
                                List<AnnotationInfo> infos = new ArrayList<>();
                                for (AnnotationInfo.Collector c : collectors) {
                                    infos.add(c.build());
                                }
                                methodAnns.put(name + descriptor, infos);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            List<AnnotationInfo> built = new ArrayList<>();
            for (AnnotationInfo.Collector c : classAnns) {
                built.add(c.build());
            }
            classes.put(internalName, new ClassInfo(internalName, superRef[0], interfaces,
                    accessRef[0], methods, source, location, built, methodAnns));
        } catch (Exception ignore) {
            // 无法解析的 class（版本过新等）跳过
        }
    }
}
