package com.spark.projectanalysis.testsupport;

import com.spark.projectanalysis.config.CallgraphPaths;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 全局测试隔离（OPT-14）：将 callgraph 数据根目录（含 projects.json / workspaces）指向临时目录，
 * 避免跑测试时污染真实 {@code D:\.callgraph}。
 *
 * <p>{@link CallgraphPaths#getHome()} 的 home 是静态缓存、跨测试用例共享；只要有任一测试
 * 在设置临时目录前触发默认解析，就会落到真实 {@code D:\.callgraph}。本扩展在每个测试类开始前
 * 强制把 {@code callgraph.home} 指向新临时目录并清空静态缓存，确保任意测试顺序下都不会写真实目录。</p>
 *
 * <p>通过 {@code META-INF/services/org.junit.jupiter.api.extension.Extension} 全局注册，对所有测试类生效。</p>
 */
public class CallgraphTempHomeExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Path tempHome = Files.createTempDirectory("callgraph-test-home");
        System.setProperty("callgraph.home", tempHome.toString());
        Field cachedHome = CallgraphPaths.class.getDeclaredField("cachedHome");
        cachedHome.setAccessible(true);
        cachedHome.set(null, null);
    }
}
