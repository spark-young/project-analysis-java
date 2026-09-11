package org.apache.dubbo.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 测试桩：Apache Dubbo 服务提供方注解。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface DubboService {
    Class<?> interfaceClass() default void.class;
    String interfaceName() default "";
    String version() default "";
    String group() default "";
}
