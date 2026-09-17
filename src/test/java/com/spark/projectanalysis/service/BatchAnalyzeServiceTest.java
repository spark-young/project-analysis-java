package com.spark.projectanalysis.service;

import com.spark.projectanalysis.service.dto.AnalyzeRequest;
import com.spark.projectanalysis.service.dto.EntryRef;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 批量分析服务：启动前的入参校验 + 任务查询。
 * 校验分支在提交异步执行器之前完成，故无需真实注册表/执行器即可覆盖。
 */
class BatchAnalyzeServiceTest {

    private final BatchAnalyzeService service = new BatchAnalyzeService(
            mock(AnalysisService.class),
            mock(AnalysisCacheService.class),
            mock(EntryListService.class));

    private static EntryRef ref(String className, String methodName) {
        EntryRef r = new EntryRef();
        r.setClassName(className);
        r.setMethodName(methodName);
        return r;
    }

    @Test
    void test_startAsync_nullRequest_badRequest() {
        AnalysisException e = assertThrows(AnalysisException.class, () -> service.startAsync(null));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage() == null || e.getMessage().contains("项目路径"));
    }

    @Test
    void test_startAsync_blankProjectPath_badRequest() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setProjectPath("   ");
        req.setEntries(Collections.singletonList(ref("com.demo.OrderService", "place")));

        AnalysisException e = assertThrows(AnalysisException.class, () -> service.startAsync(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage().contains("项目路径"), "空路径应报项目路径错误");
    }

    @Test
    void test_startAsync_emptyEntries_badRequest() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setProjectPath(System.getProperty("java.io.tmpdir"));
        req.setEntries(Collections.emptyList());

        AnalysisException e = assertThrows(AnalysisException.class, () -> service.startAsync(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage().contains("清单"), "空清单应被拒绝");
    }

    @Test
    void test_startAsync_nonExistentPath_badRequest() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setProjectPath("X:/no/such/dir-opt23-batch");
        req.setEntries(Collections.singletonList(ref("com.demo.OrderService", "place")));

        AnalysisException e = assertThrows(AnalysisException.class, () -> service.startAsync(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage().contains("不存在"), "不存在的路径应被拒绝");
    }

    @Test
    void test_status_unknownJob_returnsNull() {
        assertNull(service.status("no-such-job"));
    }
}
