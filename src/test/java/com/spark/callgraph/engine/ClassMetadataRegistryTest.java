package com.spark.callgraph.engine;

import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.RawCall;
import com.spark.callgraph.engine.model.Resolution;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class ClassMetadataRegistryTest {

    static Path libJar;
    static Path demoClasses;
    static ClassMetadataRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        libJar = Fixtures.compileToJar("lib");
        demoClasses = Fixtures.compileToDir("demo", libJar);
        registry = ClassMetadataRegistry.builder()
                .addClassesDir(demoClasses, SourceType.PROJECT)
                .addJar(libJar, SourceType.DEPENDENCY)
                .build();
    }

    @Test
    void test_knownClasses_indexed() {
        assertTrue(registry.isKnown("com/demo/OrderService"));
        assertTrue(registry.isKnown("com/demo/lib/GreeterService"));
        // JDK 类不入注册表
        assertFalse(registry.isKnown("java/util/HashMap"));
    }

    @Test
    void test_classInfo_basicFields() {
        ClassInfo info = registry.get("com/demo/OrderService");
        assertNotNull(info);
        assertEquals("java/lang/Object", info.getSuperName());
        assertEquals(SourceType.PROJECT, info.getSource());
        assertFalse(info.isInterface());
        assertFalse(info.isAbstract());

        ClassInfo repo = registry.get("com/demo/DbRepo");
        assertTrue(repo.getInterfaces().contains("com/demo/Repo"));

        ClassInfo greeter = registry.get("com/demo/Greeter");
        assertTrue(greeter.isInterface());

        ClassInfo base = registry.get("com/demo/AbstractBase");
        assertTrue(base.isAbstract());
        assertTrue(base.isMethodConcrete("format", "()Ljava/lang/String;"));
        assertFalse(base.isMethodConcrete("describe", "()Ljava/lang/String;"));
    }

    @Test
    void test_sourceOfLibClasses() {
        assertEquals(SourceType.DEPENDENCY, registry.get("com/demo/lib/GreeterService").getSource());
    }

    @Test
    void test_classesBySimpleName() {
        Collection<String> hits = registry.classesBySimpleName("OrderService");
        assertEquals(1, hits.size());
        assertTrue(hits.contains("com/demo/OrderService"));

        assertTrue(registry.classesBySimpleName("NoSuchClassXYZ").isEmpty());
    }

    @Test
    void test_methodKeysOfClass() {
        ClassInfo info = registry.get("com/demo/OrderService");
        Collection<MethodKey> keys = info.methodKeys();
        assertTrue(keys.contains(MethodKey.of("com/demo/OrderService", "place", "()V")));
        assertTrue(keys.contains(MethodKey.of("com/demo/OrderService", "pay", "(Ljava/lang/String;)V")));
        assertTrue(keys.contains(MethodKey.of("com/demo/OrderService", "pay", "(I)V")));
        assertTrue(keys.contains(MethodKey.of("com/demo/OrderService", "<init>", "()V")));
    }

    @Test
    void test_resolve_staticMethod() {
        Resolution r = registry.resolve("com/demo/Util", "now", "()J");
        assertEquals(Resolution.Kind.SINGLE, r.getKind());
        assertEquals(MethodKey.of("com/demo/Util", "now", "()J"), r.getTargets().get(0));
    }

    @Test
    void test_resolve_unknownOwner_isExternal() {
        assertEquals(Resolution.Kind.EXTERNAL, registry.resolve("com/unknown/Foo", "bar", "()V").getKind());
    }

    @Test
    void test_resolve_jdkOwner_isExcluded() {
        assertEquals(Resolution.Kind.JDK,
                registry.resolve("java/util/HashMap", "put", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;").getKind());
    }

    @Test
    void test_resolve_interfaceMethod_multipleImpls() {
        Resolution r = registry.resolve("com/demo/Greeter", "greet", "(Lcom/demo/Model;)Ljava/lang/String;");
        assertEquals(Resolution.Kind.MULTI, r.getKind());
        assertEquals(2, r.getTargets().size());
    }

    @Test
    void test_resolve_interfaceMethod_singleImpl() {
        Resolution r = registry.resolve("com/demo/Repo", "save", "(Lcom/demo/Model;)V");
        assertEquals(Resolution.Kind.MULTI, r.getKind());
        assertEquals(MethodKey.of("com/demo/DbRepo", "save", "(Lcom/demo/Model;)V"), r.getTargets().get(0));
    }

    @Test
    void test_resolve_virtualOnSubclass_walksUpHierarchy() {
        // OrderService.loop 是自身方法；DbRepo.save 在接口上声明 —— 校验具体类上的解析
        Resolution r = registry.resolve("com/demo/SubService", "format", "()Ljava/lang/String;");
        assertEquals(Resolution.Kind.SINGLE, r.getKind());
        assertEquals("com/demo/AbstractBase", r.getTargets().get(0).getOwner());
    }

    @Test
    void test_callsOf_returnsRawCalls() {
        java.util.List<RawCall> calls = registry.callsOf(MethodKey.of("com/demo/OrderService", "place", "()V"));
        assertNotNull(calls);
        assertFalse(calls.isEmpty());
        assertTrue(calls.stream().anyMatch(c -> c.getOwner().equals("com/demo/Util") && c.getName().equals("now")));
        assertTrue(calls.stream().anyMatch(c -> c.getName().equals("save")));
        assertTrue(calls.stream().anyMatch(c -> c.getName().equals("greet")));
        assertTrue(calls.stream().anyMatch(c -> c.getName().equals("<init>")));
        // 行号有效
        assertTrue(calls.stream().allMatch(c -> c.getLine() > 0));
    }
}
