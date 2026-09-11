package org.springframework.web.bind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 测试桩：Spring Web 注解。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PatchMapping {
    String name() default "";
    String[] value() default {};
    String[] path() default {};
}
