package com.spark.callgraph.service.dto;

/** 切换分支 / Tag 的请求体。 */
public class SwitchRequest {

    /** 目标分支名或 Tag 名 */
    private String ref;
    /** BRANCH / TAG */
    private String refType;

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
}
