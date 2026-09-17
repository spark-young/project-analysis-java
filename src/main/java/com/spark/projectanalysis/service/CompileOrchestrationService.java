package com.spark.projectanalysis.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 编译编排服务（OPT-18）。
 * <p>
 * 把「是否需要编译 / 用哪种方式编译 / 编译产物在哪个目录」这类<b>构建知识</b>从 Web 层
 * （原先散落在 {@link com.spark.projectanalysis.web.AnalysisController} 与
 * {@link com.spark.projectanalysis.web.ProjectsController}）收敛到一处，供 Controller
 * 复用，避免同一份候选目录规则被维护成多份。
 * <p>
 * 说明：{@link GitPrepareService} 内另有一个同名的私有 {@code findExistingArtifacts}，
 * 其语义是「仓库树里是否任意位置存在 .class（返回仓库根）」，与本类的
 * 「返回标准编译产物目录」不同，故未合并。
 */
@Service
public class CompileOrchestrationService {

    /**
     * 编译产物候选目录（相对项目根的常见标准输出目录），按优先级从高到低。
     * <p>该知识原先在 {@code AnalysisController} 与 {@code ProjectsController} 各写了一份，
     * 现统一在此维护。
     */
    public static final String[] ARTIFACT_CANDIDATE_DIRS = {
            "target/classes",   // Maven
            "build/classes",    // Gradle
            "bin",              // Eclipse 默认输出
            "build"             // javac / 通用输出根
    };

    /** 嵌套一层兜底：目录路径以这些后缀结尾也视作有效产物目录（兼容多模块 / IDE 导出布局） */
    private static final String[] NESTED_ARTIFACT_SUFFIXES = {
            "/target/classes", "/build/classes", "/out/production"
    };

    private final MavenCompileService mavenCompileService;
    private final JavacCompileService javacCompileService;

    public CompileOrchestrationService(MavenCompileService mavenCompileService,
                                       JavacCompileService javacCompileService) {
        this.mavenCompileService = mavenCompileService;
        this.javacCompileService = javacCompileService;
    }

    /**
     * 自动探测项目类型并按需编译（Maven / javac / 已有产物）。
     * <p>
     * 策略：
     * <ol>
     *   <li>非 force 时已有产物直接跳过；</li>
     *   <li>有 pom.xml → {@link MavenCompileService}（force 走 clean compile）；</li>
     *   <li>普通 Java 源码 → {@link JavacCompileService}，产物输出到 {@code build/}；</li>
     *   <li>Gradle 等其他构建工具 → 静默跳过（用户应手动编译）。</li>
     * </ol>
     *
     * @param root  项目根目录
     * @param force true 时强制 clean compile（用于「重新分析」场景）
     */
    public void compileIfNeeded(Path root, boolean force) {
        // 路径不存在是调用方传错（400），不能落到下面的通用 catch 变成"编译探测失败"的 500
        if (!Files.exists(root)) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST, "项目路径不存在: " + root);
        }
        try {
            if (!force) {
                // 非强制：已有产物就跳过
                if (findExistingArtifacts(root) != null) {
                    return;
                }
            }
            // Maven 项目
            if (Files.exists(root.resolve("pom.xml"))) {
                MavenCompileService.CompileResult r = force
                        ? mavenCompileService.compileClean(root)
                        : mavenCompileService.compile(root);
                if (!r.isSuccess()) {
                    throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR,
                            (force ? "clean compile" : "Maven 编译") + "失败：\n" + r.getOutputTail());
                }
                return;
            }
            // 普通 Java 源码 → javac（没有 clean 概念，强制时直接覆盖 build/）
            boolean hasJava;
            try (Stream<Path> walk = Files.walk(root, 100)) {
                hasJava = walk.anyMatch(p ->
                        Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"));
            }
            if (hasJava) {
                Path buildRoot = root.resolve("build");
                Files.createDirectories(buildRoot);
                JavacCompileService.CompileResult r = javacCompileService.compile(root, buildRoot);
                if (!r.isSuccess()) {
                    throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "javac 编译失败：\n" + r.getOutputTail());
                }
            }
            // Gradle 等其他构建工具 → 静默跳过（用户应手动编译）
        } catch (AnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalysisException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "编译探测失败：" + e.getMessage());
        }
    }

    /**
     * 查找已有的编译产物目录：命中候选目录（或嵌套匹配），且内部确有 .class 文件才算有效。
     *
     * @param root 项目根目录
     * @return 有效的产物目录；未找到返回 {@code null}
     */
    public Path findExistingArtifacts(Path root) {
        for (String rel : ARTIFACT_CANDIDATE_DIRS) {
            Path c = root.resolve(rel);
            if (Files.isDirectory(c) && hasClassFiles(c)) {
                return c;
            }
        }
        // 嵌套一层兜底（多模块 / IDE 导出布局）
        try (Stream<Path> walk = Files.walk(root, 3)) {
            return walk.filter(Files::isDirectory)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        for (String suffix : NESTED_ARTIFACT_SUFFIXES) {
                            if (s.endsWith(suffix)) return true;
                        }
                        return false;
                    })
                    .filter(this::hasClassFiles)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 目录或子目录下是否有 .class 文件 */
    public boolean hasClassFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".class"))
                    .findFirst().isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
