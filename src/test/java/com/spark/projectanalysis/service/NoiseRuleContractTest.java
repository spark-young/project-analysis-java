package com.spark.projectanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.projectanalysis.config.CallgraphPaths;
import com.spark.projectanalysis.service.dto.NoiseRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OPT-24 前后端噪声规则判定契约测试（后端侧）。
 *
 * <p>断言后端匹配算法 {@link NoiseRuleService#isNoiseOnRules}（私有，反射调用）与共享夹具
 * {@code src/test/resources/contract/noise.json} 完全一致。该夹具同时被前端 Node 契约测试
 * （{@code src/test/js/contract.test.js}）使用，二者必须对同一份期望值一致。</p>
 *
 * <p>为保持隔离，本测试把 callgraph 数据根目录指向临时目录（与全局 {@code CallgraphTempHomeExtension}
 * 同机制）；若 {@code GLOBAL_RULES_FILE} 静态常量未落在临时目录内（隔离缺失的兜底），再将其重定向，
 * 确保构造 {@link NoiseRuleService} 时不触碰真实 {@code D:\.callgraph}。</p>
 */
class NoiseRuleContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void isolateCallgraphHome() throws Exception {
        Path tempHome = Files.createTempDirectory("noise-contract-home");
        System.setProperty("callgraph.home", tempHome.toString());
        Field cached = CallgraphPaths.class.getDeclaredField("cachedHome");
        cached.setAccessible(true);
        cached.set(null, null);
        // 兜底：仅当 NoiseRuleService 的全局规则文件常量已落在临时目录之外时才重定向（Java 11 支持清除 final）。
        // 正常情形下全局 CallgraphTempHomeExtension（OPT-14）已把 home 指向临时目录，无需深反射，避免 JDK 告警。
        Path rulesFile = (Path) globalRulesFileField().get(null);
        String tempRoot = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().toString();
        if (rulesFile != null && !rulesFile.toAbsolutePath().toString().startsWith(tempRoot)) {
            try {
                Field fileField = globalRulesFileField();
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(fileField, fileField.getModifiers() & ~Modifier.FINAL);
                fileField.set(null, tempHome.resolve("noise-rules.json"));
            } catch (Throwable ignored) {
                // 重定向失败也不影响运行；此处仅为避免误写真实目录的兜底
            }
        }
    }

    private static Field globalRulesFileField() throws NoSuchFieldException {
        Field f = NoiseRuleService.class.getDeclaredField("GLOBAL_RULES_FILE");
        f.setAccessible(true);
        return f;
    }

    @Test
    void noiseRuleService_matchesSharedNoiseFixture() throws Exception {
        NoiseRuleService service = new NoiseRuleService();
        Method isNoiseOnRules = NoiseRuleService.class.getDeclaredMethod(
                "isNoiseOnRules", List.class, String.class, String.class);
        isNoiseOnRules.setAccessible(true);
        Method parseSig = NoiseRuleService.class.getDeclaredMethod("parseMethodSignature", String.class);
        parseSig.setAccessible(true);

        JsonNode root = loadFixture("contract/noise.json");
        JsonNode cases = root.get("cases");
        assertNotNull(cases, "夹具缺少 cases");
        assertTrue(cases.size() > 0, "夹具用例为空");

        List<String> problems = new ArrayList<>();
        for (JsonNode c : cases) {
            String name = c.get("name").asText();
            String source = c.get("source").asText();
            String identifier = c.get("identifier").asText();
            boolean expected = c.get("expected").asBoolean();

            List<NoiseRule> rules = toRules(c.get("rules"));
            boolean actual = (Boolean) isNoiseOnRules.invoke(service, rules, identifier, source);
            if (actual != expected) {
                problems.add(name + " isNoiseOnRules: expected=" + expected
                        + " actual=" + actual + " (" + identifier + ", source=" + source + ")");
            }

            // 校验 identifier 能被后端解析出夹具声明的 className / methodName / paramCount
            String[] parsed = (String[]) parseSig.invoke(null, identifier);
            if (!c.get("className").asText().equals(parsed[0])) {
                problems.add(name + " parse className: expected=" + c.get("className").asText()
                        + " actual=" + parsed[0]);
            }
            if (!c.get("methodName").asText().equals(parsed[1])) {
                problems.add(name + " parse methodName: expected=" + c.get("methodName").asText()
                        + " actual=" + parsed[1]);
            }
            if (c.get("paramCount").asInt() != Integer.parseInt(parsed[2])) {
                problems.add(name + " parse paramCount: expected=" + c.get("paramCount").asInt()
                        + " actual=" + parsed[2]);
            }
        }
        assertTrue(problems.isEmpty(), "后端 NoiseRuleService 与契约夹具不一致:\n  " + String.join("\n  ", problems));
    }

    private static List<NoiseRule> toRules(JsonNode arr) {
        List<NoiseRule> out = new ArrayList<>();
        if (arr != null) {
            for (JsonNode r : arr) {
                NoiseRule rule = new NoiseRule();
                rule.setId(r.hasNonNull("id") ? r.get("id").asText() : null);
                rule.setName(r.hasNonNull("name") ? r.get("name").asText() : null);
                rule.setMethodPattern(r.hasNonNull("methodPattern") ? r.get("methodPattern").asText() : "");
                rule.setClassPattern(r.hasNonNull("classPattern") ? r.get("classPattern").asText() : "");
                rule.setSource(r.hasNonNull("source") ? r.get("source").asText() : "ALL");
                rule.setParamCount(r.hasNonNull("paramCount") ? r.get("paramCount").asInt() : null);
                rule.setEnabled(r.path("enabled").asBoolean(true));
                out.add(rule);
            }
        }
        return out;
    }

    private static JsonNode loadFixture(String resource) throws Exception {
        try (InputStream in = NoiseRuleContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "找不到夹具资源: " + resource);
            return MAPPER.readTree(in);
        }
    }
}
