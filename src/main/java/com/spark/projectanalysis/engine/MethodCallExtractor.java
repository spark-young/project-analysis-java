package com.spark.projectanalysis.engine;

import com.spark.projectanalysis.engine.model.InvokeType;
import com.spark.projectanalysis.engine.model.RawCall;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ASM 提取单个类的全部方法体调用指令（含行号、Lambda/方法引用）。
 * 纯解析：不加载类、不执行任何代码。
 */
final class MethodCallExtractor {

    private MethodCallExtractor() {}

    // ------------------------------------------------------------------
    // jar ZipFile 会话级缓存：同一 jar 的多个类复用同一个 ZipFile，避免每类重开一次
    // ------------------------------------------------------------------
    /** 会话嵌套深度（按线程） */
    private static final ThreadLocal<Integer> SESSION_DEPTH = new ThreadLocal<>();
    /** 当前会话内 已打开 jar 路径 → ZipFile（按线程隔离，线程间不共享、不互相关闭） */
    private static final ThreadLocal<Map<Path, ZipFile>> JAR_SESSION = new ThreadLocal<>();

    /** 开启一次解析会话（可嵌套：最外层开启时初始化缓存） */
    static void beginSession() {
        int depth = SESSION_DEPTH.get() == null ? 0 : SESSION_DEPTH.get();
        if (depth == 0) JAR_SESSION.set(new HashMap<>());
        SESSION_DEPTH.set(depth + 1);
    }

    /** 结束会话：最外层结束时关闭本线程缓存的全部 ZipFile（遍历中不关闭，保证读取完整） */
    static void endSession() {
        int depth = SESSION_DEPTH.get() == null ? 0 : SESSION_DEPTH.get();
        if (depth > 1) {
            SESSION_DEPTH.set(depth - 1);
            return;
        }
        SESSION_DEPTH.remove();
        Map<Path, ZipFile> jars = JAR_SESSION.get();
        JAR_SESSION.remove();
        closeAll(jars);
    }

    private static void closeAll(Map<Path, ZipFile> jars) {
        if (jars == null) return;
        for (ZipFile z : jars.values()) {
            try {
                z.close();
            } catch (IOException ignored) {
                // 关闭失败忽略
            }
        }
    }

    /**
     * 解析 location（classes 目录或 jar 文件）中 internalName 的方法体。
     *
     * @return name+desc -> RawCall 列表
     */
    static Map<String, List<RawCall>> extract(Path location, String internalName) throws IOException {
        byte[] bytes = readClassBytes(location, internalName);
        if (bytes == null) return new HashMap<>();
        ClassReader reader = new ClassReader(bytes);
        Map<String, List<RawCall>> out = new HashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                List<RawCall> calls = new ArrayList<>();
                out.put(name + descriptor, calls);
                int[] line = {0};
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLineNumber(int n, org.objectweb.asm.Label start) {
                        line[0] = n;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name1,
                                                String descriptor1, boolean isInterface) {
                        InvokeType type;
                        switch (opcode) {
                            case Opcodes.INVOKESTATIC: type = InvokeType.STATIC; break;
                            case Opcodes.INVOKEINTERFACE: type = InvokeType.INTERFACE; break;
                            case Opcodes.INVOKESPECIAL: type = InvokeType.SPECIAL; break;
                            case Opcodes.INVOKEDYNAMIC: type = InvokeType.DYNAMIC; break;
                            default: type = InvokeType.VIRTUAL; break;
                        }
                        calls.add(new RawCall(owner, name1, descriptor1, type, line[0]));
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name1, String descriptor1,
                                                       Handle bootstrapMethodHandle,
                                                       Object[] bootstrapMethodArguments) {
                        // Lambda / 方法引用：bootstrap 参数中的 Handle 指向实现方法
                        if (bootstrapMethodArguments != null) {
                            for (Object arg : bootstrapMethodArguments) {
                                if (arg instanceof Handle) {
                                    Handle h = (Handle) arg;
                                    calls.add(new RawCall(h.getOwner(), h.getName(), h.getDesc(),
                                            InvokeType.DYNAMIC, line[0]));
                                }
                            }
                        }
                    }
                };
            }
        }, 0);
        return out;
    }

    private static byte[] readClassBytes(Path location, String internalName) throws IOException {
        if (Files.isDirectory(location)) {
            Path file = location.resolve(internalName + ".class");
            if (!Files.exists(file)) return null;
            return Files.readAllBytes(file);
        }
        Map<Path, ZipFile> jars = JAR_SESSION.get();
        if (jars == null) {
            // 无会话（直接单次调用）：即用即关，保持改前语义，避免文件句柄泄漏
            try (ZipFile zip = new ZipFile(location.toFile())) {
                return readEntry(zip, internalName);
            }
        }
        Path key = location.toAbsolutePath().normalize();
        ZipFile zip = jars.get(key);
        if (zip == null) {
            zip = new ZipFile(location.toFile());
            jars.put(key, zip);
        }
        return readEntry(zip, internalName);
    }

    private static byte[] readEntry(ZipFile zip, String internalName) throws IOException {
        ZipEntry entry = zip.getEntry(internalName + ".class");
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
