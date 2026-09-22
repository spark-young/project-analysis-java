package com.spark.projectanalysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.projectanalysis.service.ProjectRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 OPT-10 的 token 脱敏策略：
 * - API 响应所用的 mapper（带 MixIn）不应序列化 gitToken；
 * - 持久化所用的独立 mapper（不带 MixIn）应正常序列化并回读 gitToken。
 */
class RegisteredProjectApiMixinTest {

    /** 带 MixIn 的 mapper（模拟 API 响应）不应序列化 gitToken */
    @Test
    void test_httpMapper_hidesGitToken() throws Exception {
        ObjectMapper httpMapper = Jackson2ObjectMapperBuilder.json()
                .mixIn(ProjectRegistry.RegisteredProject.class, RegisteredProjectApiMixin.class)
                .build();

        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = "x";
        p.gitToken = "ghp_secret_token";
        p.gitUsername = "u";

        String json = httpMapper.writeValueAsString(p);
        assertFalse(json.contains("ghp_secret_token"), "API 响应不应包含 gitToken: " + json);
        assertTrue(json.contains("\"id\""), "其他字段应正常序列化");
    }

    /** 不带 MixIn 的 mapper（模拟 projects.json 持久化）应正常序列化并回读 gitToken */
    @Test
    void test_persistenceMapper_keepsGitToken() throws Exception {
        ObjectMapper persistenceMapper = new ObjectMapper();

        ProjectRegistry.RegisteredProject p = new ProjectRegistry.RegisteredProject();
        p.id = "x";
        p.gitToken = "ghp_secret_token";

        String json = persistenceMapper.writeValueAsString(p);
        assertTrue(json.contains("ghp_secret_token"), "持久化应保留 gitToken: " + json);

        ProjectRegistry.RegisteredProject back =
                persistenceMapper.readValue(json, ProjectRegistry.RegisteredProject.class);
        assertEquals("ghp_secret_token", back.gitToken);
    }
}
