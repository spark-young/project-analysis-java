package com.spark.callgraph.web;

import com.spark.callgraph.service.AnalysisException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一错误响应：{"error": "..."} */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysis(AnalysisException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        return ResponseEntity.status(500).body(body("内部错误: " + e.getMessage()));
    }

    private Map<String, Object> body(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", message);
        return map;
    }
}
