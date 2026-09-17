package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.dto.AnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
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
}
