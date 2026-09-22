package com.spark.projectanalysis.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OPT-24 前后端签名契约测试（后端侧）。
 *
 * <p>断言后端权威签名格式 {@link MethodKey} 与共享夹具
 * {@code src/test/resources/contract/signatures.json} 完全一致。该夹具同时被前端
 * Node 契约测试（{@code src/test/js/contract.test.js}）使用——两端对同一份期望值负责。</p>
 *
 * <p>本测试放在 {@code engine.model} 包内，以便直接调用包级可见的 {@link MethodKey#parseParams(String)}。</p>
 */
class MethodSignatureContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void methodKey_matchesSharedSignatureFixture() throws Exception {
        JsonNode root = loadFixture("contract/signatures.json");
        JsonNode cases = root.get("cases");
        assertNotNull(cases, "夹具缺少 cases");
        assertTrue(cases.size() > 0, "夹具用例为空");

        List<String> problems = new ArrayList<>();
        for (JsonNode c : cases) {
            String name = c.get("name").asText();
            String owner = c.get("owner").asText();
            String className = c.get("className").asText();
            String methodName = c.get("methodName").asText();
            String descriptor = c.get("descriptor").asText();
            String identifier = c.get("identifier").asText();
            String display = c.get("display").asText();
            List<String> paramTypes = toStringList(c.get("paramTypes"));

            MethodKey key = new MethodKey(owner, methodName, descriptor);

            if (!className.equals(key.getClassName())) {
                problems.add(name + " getClassName: expected=" + className + " actual=" + key.getClassName());
            }
            if (!identifier.equals(key.getIdentifier())) {
                problems.add(name + " getIdentifier: expected=" + identifier + " actual=" + key.getIdentifier());
            }
            if (!display.equals(key.getDisplay())) {
                problems.add(name + " getDisplay: expected=" + display + " actual=" + key.getDisplay());
            }
            List<String> parsed = MethodKey.parseParams(descriptor);
            if (!paramTypes.equals(parsed)) {
                problems.add(name + " parseParams: expected=" + paramTypes + " actual=" + parsed);
            }
        }
        assertTrue(problems.isEmpty(), "后端 MethodKey 与契约夹具不一致:\n  " + String.join("\n  ", problems));
    }

    private static JsonNode loadFixture(String resource) throws Exception {
        try (InputStream in = MethodSignatureContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "找不到夹具资源: " + resource);
            return MAPPER.readTree(in);
        }
    }

    private static List<String> toStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            arr.forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
