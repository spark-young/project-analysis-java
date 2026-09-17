package com.spark.projectanalysis.config;

import com.spark.projectanalysis.service.ProjectRegistry;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 配置：为 HTTP（API 响应）序列化注册 MixIn，隐藏
 * {@link ProjectRegistry.RegisteredProject#gitToken}（OPT-10 安全项）。
 *
 * <p>该 Customizer 仅作用于 Spring Boot 自动装配的主 ObjectMapper（HTTP 消息转换器所用），
 * 不影响各 Service 内部自行 {@code new ObjectMapper()} 的实例（如 ProjectRegistry 的持久化）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer gitTokenHidingCustomizer() {
        return builder -> builder.mixIn(
                ProjectRegistry.RegisteredProject.class,
                RegisteredProjectApiMixin.class);
    }
}
