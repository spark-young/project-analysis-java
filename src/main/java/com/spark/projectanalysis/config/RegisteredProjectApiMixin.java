package com.spark.projectanalysis.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.spark.projectanalysis.service.ProjectRegistry;

/**
 * Jackson MixIn：仅作用于 Spring MVC 的 HTTP 序列化（API 响应），
 * 隐藏 {@link ProjectRegistry.RegisteredProject#gitToken}，避免认证凭据经
 * {@code /api/projects} 等接口泄露给前端（OPT-10）。
 *
 * <p>关键点：{@link ProjectRegistry} 内部使用独立的 {@code new ObjectMapper()} 实例做
 * {@code projects.json} 持久化，不受此 MixIn 影响，因此 gitToken 仍会被正常写入与读取，
 * 从而支持克隆 / 列引用时的 token 复用。若直接在 DTO 字段上标注 {@code @JsonIgnore}，
 * 会因为共用同一套注解而同时破坏持久化（令牌无法复用、旧 JSON 反序列化失效）。
 */
public abstract class RegisteredProjectApiMixin {

    /** 仅抑制 HTTP 响应中的序列化；不影响反序列化（仍由 setGitToken 处理）。 */
    @JsonIgnore
    public abstract String getGitToken();
}
