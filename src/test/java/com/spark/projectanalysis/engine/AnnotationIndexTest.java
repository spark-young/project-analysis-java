package com.spark.projectanalysis.engine;

import com.spark.projectanalysis.engine.model.AnnotationInfo;
import com.spark.projectanalysis.engine.model.ClassInfo;
import com.spark.projectanalysis.engine.model.SourceType;
import com.spark.projectanalysis.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 注解索引：类/方法级注解及属性（入口探测的底层支撑）。 */
class AnnotationIndexTest {

    static ClassMetadataRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        registry = ClassMetadataRegistry.builder()
                .addClassesDir(Fixtures.compileToDir("entries"), SourceType.PROJECT)
                .build();
    }

    @Test
    void test_classLevelAnnotations_recorded() {
        ClassInfo info = registry.get("com/demo/OrderController");
        assertNotNull(info);

        AnnotationInfo rc = info.annotation("org/springframework/web/bind/annotation/RestController");
        assertNotNull(rc, "类上应记录 @RestController");

        AnnotationInfo rm = info.annotation("org/springframework/web/bind/annotation/RequestMapping");
        assertNotNull(rm, "类上应记录 @RequestMapping");
        assertEquals("/api/orders", rm.first("value"), "类级 RequestMapping 的路径属性");
    }

    @Test
    void test_methodLevelAnnotations_recorded() {
        ClassInfo info = registry.get("com/demo/OrderController");

        AnnotationInfo gm = info.methodAnnotation("list", "()Ljava/lang/String;",
                "org/springframework/web/bind/annotation/GetMapping");
        assertNotNull(gm, "方法上应记录 @GetMapping");
        assertEquals("/list", gm.first("value"));

        // 枚举数组属性（method={RequestMethod.DELETE}）应记录枚举常量名
        AnnotationInfo rm = info.methodAnnotation("remove", "(J)Ljava/lang/String;",
                "org/springframework/web/bind/annotation/RequestMapping");
        assertNotNull(rm);
        assertEquals("DELETE", rm.first("method"));
        assertEquals("/{id}", rm.first("value"));
    }

    @Test
    void test_unAnnotatedMethod_hasNoAnnotations() {
        ClassInfo info = registry.get("com/demo/OrderController");
        assertTrue(info.methodAnnotations("helper", "()Ljava/lang/String;").isEmpty(),
                "无注解方法的方法级注解应为空");
    }

    @Test
    void test_classValueAttribute_recordedAsTypeDescriptor() {
        ClassInfo info = registry.get("com/demo/PayRpcImpl");
        AnnotationInfo s = info.annotation("org/apache/dubbo/config/annotation/DubboService");
        assertNotNull(s);
        assertEquals("Lcom/demo/api/PayApi;", s.first("interfaceClass"),
                "Class 属性应记录为类型描述符");
    }
}
