package com.spark.callgraph.service;

import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用 JDK 自带 javac 编译"无构建工具"的普通 Java 工程。
 * <p>
 * 适配场景：Git 拉取的纯源码工程，既无 pom.xml 也无 build.gradle。
 * 策略：
 * - 递归收集源码目录下全部 .java（跳过 target/build/.git/.idea/out 等构建与配置目录）；
 * - 递归收集仓库内 *.jar 作为编译 classpath，并复制到 buildRoot/lib 供分析端引用；
 * - 编译产物输出到 buildRoot/classes，整体形成"classes + lib"布局，复用 ClasspathResolver 的既有识别逻辑。
 * <p>
 * 仅执行标准 javac 编译，不附加 source/target 开关（避免 JDK≥17 对旧 source 报错，见项目工程教训）。
 */
@Service
public class JavacCompileService {

    private static final int MAX_YIELD = 20_000;

    /** 需要跳过的构建/配置目录名 */
    private static boolean skipDir(String name) {
        return name.equals(".git") || name.equals(".svn") || name.equals(".idea")
                || name.equals("target") || name.equals("build") || name.equals("out");
    }

    /**
     * 编译普通 Java 工程源码。
     *
     * @param srcRoot   源码根目录（克隆目录）
     * @param buildRoot 产物根目录（须已存在），编译输出到 {@code buildRoot/classes}，依赖 jar 复制到 {@code buildRoot/lib}
     * @return 编译结果（含产物布局目录与输出 tail）
     */
    public CompileResult compile(Path srcRoot, Path buildRoot) {
        List<Path> sources = new ArrayList<>();
        List<Path> libJars = new ArrayList<>();
        try {
            collect(srcRoot, sources, libJars);
        } catch (IOException e) {
            return CompileResult.failure("扫描普通 Java 工程源码时出错：" + e.getMessage());
        }

        if (sources.isEmpty()) {
            return CompileResult.failure("目录下未找到任何 .java 源码文件，无法用 javac 编译。");
        }

        try {
            Path classes = buildRoot.resolve("classes");
            Path lib = buildRoot.resolve("lib");
            Files.createDirectories(classes);

            // 1) 复制仓库内 jar 到 lib（分析端按 classes+lib 布局读取依赖）
            for (Path jar : libJars) {
                Path target = lib.resolve(jar.getFileName().toString());
                Files.copy(jar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 2) 组装 javac 参数
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return CompileResult.failure("当前 JRE 未提供 javax.tools.JavaCompiler（可能不是完整 JDK）。"
                        + "普通 Java 工程的 javac 编译需要完整的 JDK 环境。");
            }

            List<String> args = new ArrayList<>();
            args.add("-d");
            args.add(classes.toString());
            args.add("-encoding");
            args.add("UTF-8");
            if (!libJars.isEmpty()) {
                String cp = libJars.stream().map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator));
                args.add("-classpath");
                args.add(cp);
            }
            for (Path s : sources) {
                args.add(s.toString());
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StringWriter err = new StringWriter();
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);
                 PrintWriter pw = new PrintWriter(err)) {
                Boolean ok = compiler.getTask(pw, fm, diagnostics, args, null,
                        fm.getJavaFileObjects(sources.stream().map(Path::toFile).toArray(File[]::new))).call();
                if (Boolean.TRUE.equals(ok)) {
                    return CompileResult.success();
                }
                // 拼装诊断信息（前若干条，帮助定位缺依赖/语法错误）
                List<Diagnostic<? extends JavaFileObject>> ds = diagnostics.getDiagnostics();
                StringBuilder sb = new StringBuilder("javac 编译失败，共 " + ds.size() + " 条诊断：\n");
                int shown = 0;
                for (Diagnostic<? extends JavaFileObject> d : ds) {
                    if (shown >= 8) break;
                    String srcName = d.getSource() != null
                            ? new File(d.getSource().getName()).getName() : "?";
                    sb.append("  [").append(d.getKind().name()).append("] ")
                            .append(srcName).append(':').append(d.getLineNumber())
                            .append(" ").append(d.getMessage(null)).append('\n');
                    shown++;
                }
                if (err.toString().trim().length() > 0) {
                    sb.append("--- javac 输出 ---\n").append(err);
                }
                return CompileResult.failure(sb.toString());
            } catch (IOException e) {
                return CompileResult.failure("javac 编译 IO 异常：" + e.getMessage());
            }
        } catch (IOException e) {
            return CompileResult.failure("准备 javac 编译目录时出错：" + e.getMessage());
        }
    }

    /** 递归收集 .java 源文件与仓库内 .jar（跳过构建/配置目录） */
    private void collect(Path root, List<Path> sources, List<Path> jars) throws IOException {
        try (Stream<Path> walk = Files.walk(root, Math.max(1, MAX_YIELD / 100))) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String n = p.getFileName().toString();
                // 跳过位于忽略目录下的文件
                boolean insideSkipped = false;
                for (Path parent = p.getParent(); parent != null && parent.startsWith(root);
                     parent = parent.getParent()) {
                    if (parent.equals(root)) break;
                    if (skipDir(parent.getFileName().toString())) { insideSkipped = true; break; }
                }
                if (insideSkipped) return;
                if (n.endsWith(".java")) sources.add(p.toAbsolutePath().normalize());
                else if (n.endsWith(".jar")) jars.add(p.toAbsolutePath().normalize());
            });
        }
    }

    /** 编译结果（接口与 MavenCompileService.CompileResult 对齐，便于调用方统一处理） */
    public static final class CompileResult {
        private final boolean success;
        private final String outputTail;

        private CompileResult(boolean success, String outputTail) {
            this.success = success;
            this.outputTail = outputTail == null ? "" : outputTail;
        }

        public static CompileResult success() { return new CompileResult(true, ""); }
        public static CompileResult failure(String outputTail) { return new CompileResult(false, outputTail); }

        public boolean isSuccess() { return success; }
        public String getOutputTail() { return outputTail; }
    }
}