package com.spark.projectanalysis.service.dto;

import java.util.List;

/**
 * 入口校验结果（{@code GET /api/classes/verify} 响应，OPT-28）。
 *
 * <p>由 {@code AnalysisService#verifyEntry} 的 {@code Map<String,Object>} 类型化而来，
 * <b>字段名 / 出现条件 / 类型与原 Map 的 key 逐一对应</b>（线上 JSON 形状契约，不可变更）：</p>
 *
 * <ul>
 *   <li>{@code ok} / {@code reason}：恒出现；</li>
 *   <li>{@code candidates}：仅当按 simpleName 命中多个同名类时出现；</li>
 *   <li>{@code available}：仅当类存在但方法名不存在时出现；</li>
 *   <li>{@code descriptor}：仅当不指定 descriptor 且恰好匹配唯一重载时出现；</li>
 *   <li>{@code multipleOverloads}：仅当不指定 descriptor 且存在多个重载时出现。</li>
 * </ul>
 *
 * <p>条件字段默认 {@code null}，配合 {@code spring.jackson.default-property-inclusion: non_null}
 * 序列化时省略 —— 与原 Map「不 put 该 key」的行为完全等价。</p>
 */
public class EntryVerifyResult {

    /** 是否通过校验 */
    private boolean ok;
    /** 校验结论 / 失败原因（人可读） */
    private String reason;
    /** 同名类全限定名列表（仅同名类 &gt;1 时非 null） */
    private List<String> candidates;
    /** 类内可用方法名列表（仅方法不存在时非 null） */
    private List<String> available;
    /** 建议使用的 JVM 描述符（仅唯一重载时非 null） */
    private String descriptor;
    /** 候选重载的 JVM 描述符列表（仅多重载时非 null） */
    private List<String> multipleOverloads;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getCandidates() { return candidates; }
    public void setCandidates(List<String> candidates) { this.candidates = candidates; }
    public List<String> getAvailable() { return available; }
    public void setAvailable(List<String> available) { this.available = available; }
    public String getDescriptor() { return descriptor; }
    public void setDescriptor(String descriptor) { this.descriptor = descriptor; }
    public List<String> getMultipleOverloads() { return multipleOverloads; }
    public void setMultipleOverloads(List<String> multipleOverloads) { this.multipleOverloads = multipleOverloads; }
}
