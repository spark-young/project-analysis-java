package com.spark.callgraph.engine.entry;

import com.spark.callgraph.engine.ClassMetadataRegistry;

import java.util.List;

/**
 * 入口探测器 SPI：按框架特征从注册表中识别业务入口方法。
 * 扩展方式：实现本接口并注册为 Spring Bean 即可参与扫描。
 */
public interface EntryPointDetector {

    /** 入口类型码（分组键），如 REST / DUBBO / ELASTIC_JOB / MAIN */
    String type();

    /** 展示标签 */
    String label();

    /** 从注册表识别入口 */
    List<EntryPoint> detect(ClassMetadataRegistry registry);
}
