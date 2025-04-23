package com.sky.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptimisticLock {
    /**
     * 重试次数
     */
    int maxRetries() default 3;

    /**
     * 要更新的实体类参数名
     */
    String entityParam() default "orders";
}
