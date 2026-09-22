package com.spark.projectanalysis.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 编译产物目录探测服务（OPT-18）。
 * <p>
 * 把「编译产物在哪个目录」这类<b>构建知识</b>从 Web 层（原先散落在
 * {@link com.spark.projectanalysis.web.ProjectsController}）收敛到一处，供 Controller
 * 复用，避免同一份候选目录规则被维护成多份。
 * <p>
 * 说明（OPT-17/18 清理）：原 {@code compileIfNeeded}（自动探测项目类型并按需编译）的唯一
 * 调用方 {@code /api/ensure-compile} 端点已于 OPT-17 删除，该方法在源码与测试中均无引用，
 * 属死代码，已随本次清理移除；连带移除仅服务于它的 {@code MavenCompileService} /
 * {@code JavacCompileService} 依赖与构造器。本类现仅保留<b>只读</b>的产物目录探测能力。
 * <p>
 * 另：{@link GitPrepareService} 内还有一个同名的私有 {@code findExistingArtifacts}，
 * 其语义是「仓库树里是否任意位置存在 .class（返回仓库根）」，与本类的
 * 「返回标准编译产物目录」不同，故未合并。
 */
@Service
public class CompileOrchestrationService {

    /**
     * 编译产物候选目录（相对项目根的常见标准输出目录），按优先级从高到低。
     * <p>该知识原先在 {@code ProjectsController} 等多处各写了一份，现统一在此维护。
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
