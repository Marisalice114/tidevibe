package com.sky.service;


public interface RateLimiterService {
    /**
     * 使用简单的固定窗口限流
     * @param key 限流标识
     * @param windowSeconds 窗口期(秒)
     * @param limit 限流阈值
     * @return 是否允许访问
     */
    boolean isAllowed(String key, int windowSeconds, int limit);

    /**
     * 使用令牌桶算法限流
     * @param key 限流标识
     * @param capacity 令牌桶容量
     * @param rate 令牌填充速率(每秒)
     * @param requested 请求令牌数
     * @return 是否允许访问
     */
    boolean isAllowedByTokenBucket(String key, int capacity, int rate, int requested);
}
