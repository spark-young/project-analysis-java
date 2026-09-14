package com.spark.callgraph.service.dto;

/**
 * 项目级缓存文件元信息（仅用于列表展示，不含分析结果内容）。
 */
public class CacheFileInfo {
    /** 文件名，如 OrderController#createOrder_abcd1234.json */
    public String fileName;
    /** 显示名称（解析自文件名，用于下拉展示） */
    public String displayName;
    /** 文件大小（字节） */
    public long sizeBytes;
    /** 最后修改时间（epoch millis） */
    public long lastModifiedMs;

    public CacheFileInfo() {}

    public CacheFileInfo(String fileName, long sizeBytes, long lastModifiedMs) {
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.lastModifiedMs = lastModifiedMs;
        this.displayName = humanize(fileName);
    }

    /** 从文件名解析友好展示名 */
    private static String humanize(String fileName) {
        if (fileName == null) return "";
        String noExt = fileName;
        int dot = noExt.lastIndexOf('.');
        if (dot >= 0) noExt = noExt.substring(0, dot);
        // BatchAnalysis → "全量分析(N个入口)"
        if (noExt.startsWith("__BatchAnalysis")) {
            return noExt.replace("__BatchAnalysis", "全量分析");
        }
        return noExt;
    }
}
