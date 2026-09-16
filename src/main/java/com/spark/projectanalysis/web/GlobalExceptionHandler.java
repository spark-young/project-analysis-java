package com.spark.projectanalysis.web;

import com.spark.projectanalysis.service.AnalysisException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一错误响应：{"error": "..."} */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysis(AnalysisException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(e.getMessage()));
    }

    /** 客户端主动断开连接（如下载中途关闭页面），无需返回错误响应 */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e) {
        log.debug("客户端断开连接: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("内部错误: " + e.getMessage()));
    }

    private Map<String, Object> body(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", message);
        return map;
    }
}
