package com.spark.callgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java 方法调用链静态分析工具。
 * 内网安全设计：默认仅绑定 127.0.0.1，零出站网络行为，对被分析项目只读不执行。
 */
@SpringBootApplication
public class CallGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallGraphApplication.class, args);
    }
}
