package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.dto.AnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存服务：按文件名加载的入参安全校验。
 * 背景：fileName 直接来自请求参数，Path.resolve 遇到绝对路径会原样返回该绝对路径，
 * 等同于任意文件读；".." 则可穿越出缓存目录。
 */
class AnalysisCacheServiceTest {

    @TempDir
    Path project;

    private final AnalysisCacheService service = new AnalysisCacheService();

    /** 先落一份真实缓存，返回其合法文件名 */
    private String writeOneCache() {
        AnalysisResult result = new AnalysisResult();
        service.save(project.toString(), "com.demo.OrderController", "list", 20, 50000, "ALL", result);
        return service.fileNameOf("com.demo.OrderController", "list", 20, 50000, "ALL");
    }

    @Test
    void test_loadByFileName_legalName_hit() {
        String name = writeOneCache();

        Optional<AnalysisResult> r = service.loadByFileName(project.toString(), name);

        assertTrue(r.isPresent(), "合法文件名应能正常读出缓存: " + name);
    }

    @Test
    void test_loadByFileName_parentTraversal_rejected() {
        writeOneCache();

        AnalysisException e = assertThrows(AnalysisException.class,
                () -> service.loadByFileName(project.toString(), "../secret.json"),
                "含 .. 的文件名必须被拒绝");
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void test_loadByFileName_absolutePath_rejected() {
        writeOneCache();

        AnalysisException e = assertThrows(AnalysisException.class,
                () -> service.loadByFileName(project.toString(), "C:\\Windows\\win.ini"),
                "跨盘符绝对路径必须被拒绝");
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void test_loadByFileName_empty_rejected() {
        AnalysisException empty = assertThrows(AnalysisException.class,
                () -> service.loadByFileName(project.toString(), ""),
                "空文件名必须被拒绝");
        assertEquals(HttpStatus.BAD_REQUEST, empty.getStatus());

        AnalysisException nullName = assertThrows(AnalysisException.class,
                () -> service.loadByFileName(project.toString(), null),
                "null 文件名必须被拒绝");
        assertEquals(HttpStatus.BAD_REQUEST, nullName.getStatus());
    }

    /**
     * jar 型项目：projectPath 指向文件（fat jar）时，缓存目录应落在 {@code <jar>.callgraph/cache}，
     * 而非 jar 内部（jar 是文件，无法在其中建目录）。见 {@code CallgraphPaths.projectDataDir}。
     */
    @Test
    void test_jarProject_cacheLandsUnderJarNamedCallgraphDir() throws Exception {
        Path jar = project.resolve("app.jar");
        Files.write(jar, new byte[]{0x50, 0x4B});

        service.saveSingle(jar.toString(), Map.of("k", "v"), "hash-1", 1);

        Path jarCacheDir = Paths.get(jar.toString() + ".callgraph").resolve("cache");
        assertTrue(Files.isRegularFile(jarCacheDir.resolve(AnalysisCacheService.SINGLE_CACHE_FILE)),
                "jar 型项目的缓存应写在 <jar>.callgraph/cache 下: " + jarCacheDir);

        // 目录型项目应写 <dir>/.callgraph/cache，与 jar 型不复用同一位置
        Path dirCacheFile = project.resolve(".callgraph").resolve("cache")
                .resolve(AnalysisCacheService.SINGLE_CACHE_FILE);
        assertTrue(!Files.exists(dirCacheFile),
                "jar 型项目不应把缓存写到目录型位置: " + dirCacheFile);
    }
}
