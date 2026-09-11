package com.spark.callgraph.service;

import org.springframework.http.HttpStatus;

/** 业务异常：带 HTTP 状态码 */
public class AnalysisException extends RuntimeException {

    private final HttpStatus status;

    public AnalysisException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
