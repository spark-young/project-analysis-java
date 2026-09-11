package com.alibaba.dubbo.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 测试桩：Alibaba Dubbo（旧包名）服务提供方注解。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Deprecated
public @interface Service {
    Class<?> interfaceClass() default void.class;
    String interfaceName() default "";
    String version() default "";
    String group() default "";
}
