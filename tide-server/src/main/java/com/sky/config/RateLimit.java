package com.sky.config;

import com.sky.constant.MessageConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 用于标记需要进行访问频率限制的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key前缀
     * 支持使用 {userId} 和 {ip} 作为变量，运行时会被实际值替换
     */
    String key() default "";
    /**
     * 时间窗口(秒)
     */
    int windowSeconds() default 1;

    /**
     * 限流阈值(窗口期内最大请求数)
     */
    int limit() default 10;

    /**
     * 限流类型: 0-固定窗口, 1-令牌桶
     */
    int type() default 0;

    /**
     * 令牌桶专用-令牌桶容量
     */
    int capacity() default 10;

    /**
     * 令牌桶专用-令牌填充速率(每秒)
     */
    int rate() default 10;

    /**
     * 令牌桶专用-每次请求消耗令牌数
     */
    int requested() default 1;

    /**
     * 限流提示消息
     */
    String message() default MessageConstant.REQUEST_FREQUENT;
}
