package com.spark.projectanalysis.service;

import com.spark.projectanalysis.engine.entry.EntryPointDetector;
import com.spark.projectanalysis.service.dto.EntryScanStatus;
import com.spark.projectanalysis.service.dto.ScanStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 入口扫描编排：入参校验（空/不存在路径 → 400）+ 异步任务生命周期。
 * 校验在构建注册表（重的步骤）之前完成，故无需真实注册表。
 */
class EntryScanServiceTest {

    private final ClassMetadataService classMetadataService = mock(ClassMetadataService.class);
    private final ScanStrategyService strategyService = mock(ScanStrategyService.class);
    private EntryScanService service;

    /** 每次返回一个空白策略（activeProfile() 为 null），使扫描在校验阶段即结束，避免触碰真实注册表。 */
    private EntryScanService newService() {
        when(strategyService.effectiveForProject(any())).thenReturn(new ScanStrategy());
        service = new EntryScanService(classMetadataService,
                Collections.<EntryPointDetector>emptyList(), strategyService);
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void test_scan_blankPath_badRequest() {
        newService();

        AnalysisException e = assertThrows(AnalysisException.class, () -> service.scan("   "));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage().contains("不能为空"));
    }

    @Test
    void test_scan_nullPath_badRequest() {
        newService();

        AnalysisException e = assertThrows(AnalysisException.class,
                () -> service.scan((String) null));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void test_scan_nonExistentPath_badRequest() {
        newService();

        AnalysisException e = assertThrows(AnalysisException.class,
                () -> service.scan("X:/no/such/dir-opt23-scan"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getMessage().contains("不存在"), "路径不存在应返回 400 语义");
    }

    @Test
    void test_scanWithProfileId_nonExistentPath_badRequest() {
        newService();

        AnalysisException e = assertThrows(AnalysisException.class,
                () -> service.scan("X:/no/such/dir-opt23-scan", "some-profile"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void test_status_unknownJob_returnsNull() {
        newService();
        assertNull(service.status("no-such-job"));
    }

    @Test
    void test_startAsync_nonExistentPath_eventuallyFails() throws Exception {
        EntryScanService svc = newService();

        String jobId = svc.startAsync("X:/no/such/dir-opt23-scan-async");
        assertNotNull(jobId);
        assertNotNull(svc.status(jobId), "刚启动的 job 应立即可查询");

        EntryScanStatus st = awaitTerminal(svc, jobId);
        assertEquals(EntryScanStatus.State.FAILED, st.getState(), "不存在的路径应使异步扫描失败");
        assertNotNull(st.getError());
        assertTrue(st.getError().contains("不存在"));
    }

    private static EntryScanStatus awaitTerminal(EntryScanService svc, String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        EntryScanStatus st = svc.status(jobId);
        while (System.currentTimeMillis() < deadline) {
            st = svc.status(jobId);
            if (st != null && (st.getState() == EntryScanStatus.State.FAILED
                    || st.getState() == EntryScanStatus.State.DONE)) {
                return st;
            }
            Thread.sleep(20);
        }
        return st;
    }
}
