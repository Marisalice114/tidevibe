package com.sky.service.impl;

import com.sky.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.functors.TruePredicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


/**
 * redis限流服务
 */
@Slf4j
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    @Autowired
    private StringRedisTemplate redisTemplate;


    /**
     * 简单的固定窗口限流Lua脚本
     */
    private final String simpleRateLimiterScript =
            "local key = KEYS[1] " +
                    "local window = tonumber(ARGV[1]) " +
                    "local limit = tonumber(ARGV[2]) " +

                    "local current = tonumber(redis.call('get', key) or '0') " +
                    "if current + 1 > limit then " +
                    "    return 0 " +
                    "else " +
                    "    redis.call('incrby', key, 1) " +
                    "    redis.call('expire', key, window) " +
                    "    return 1 " +
                    "end";
    /**
     * 假设设置每60秒限制5次请求：
     *
     * 第1-5次请求：允许通过，计数从1增加到5
     * 第6次及以后请求：在当前60秒窗口内被拒绝
     * 60秒后，计数器过期，重新开始计数
     */

    /**
     * 令牌桶算法实现的Lua脚本
     */
    private final String tokenBucketScript =
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local timestamp = tonumber(ARGV[2]) " +
                    "local rate = tonumber(ARGV[3]) " +
                    "local requested = tonumber(ARGV[4]) " +

                    "local last_tokens = tonumber(redis.call('hget', key, 'tokens') or capacity) " +
                    "local last_refreshed = tonumber(redis.call('hget', key, 'last_refreshed') or '0') " +

                    "local elapsed = math.max(0, timestamp - last_refreshed) " +
                    "local new_tokens = math.min(capacity, last_tokens + (elapsed/1000.0) * rate) " +

                    "local allowed = 0 " +
                    "if new_tokens >= requested then " +
                    "    new_tokens = new_tokens - requested " +
                    "    allowed = 1 " +
                    "end " +

                    "redis.call('hset', key, 'tokens', new_tokens) " +
                    "redis.call('hset', key, 'last_refreshed', timestamp) " +
                    "redis.call('expire', key, 3600) " +

                    "return allowed";

    /**
     * 假设设置容量为10，速率为2个/秒：
     * 初始状态：桶中有10个令牌
     * 每秒自动添加2个令牌，但不超过容量10个
     * 请求到达时消耗令牌，不够则拒绝
     * 允许突发流量（最多10个并发请求）
     * 长期平均速率保持在2个/秒
     */

    @Override
    public boolean isAllowed(String key, int windowSeconds, int limit) {
        try {
            // 创建Redis脚本对象
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(simpleRateLimiterScript);  // 设置脚本内容
            script.setResultType(Long.class);  // 设置返回值类型
            // 准备执行脚本的参数
            List<String> keys = Collections.singletonList(key);  // Redis键列表
            // 执行Lua脚本
            Long result = redisTemplate.execute(
                    script,
                    keys,  // KEYS数组
                    String.valueOf(windowSeconds),  // 窗口期(ARGV[1])
                    String.valueOf(limit)  // 限流阈值(ARGV[2])
            );

            // 判断结果：1=允许访问，0=被限流
            return result != null && result == 1L;
        } catch (Exception e) {
            // 发生异常时记录日志
            log.error("固定窗口限流异常，key: {}, 异常信息: {}", key, e.getMessage(), e);
            //redis出现故障的时候，为了不影响主业务流程运行，这里出现异常是直接放行的
            return true;
        }
    }

    @Override
    public boolean isAllowedByTokenBucket(String key, int capacity, int rate, int requested) {
        try {
            // 创建Redis脚本对象
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(tokenBucketScript);  // 设置脚本内容
            script.setResultType(Long.class);  // 设置返回值类型

            // 准备执行脚本的参数
            List<String> keys = Collections.singletonList(key);  // Redis键列表
            // 执行Lua脚本
            Long result = redisTemplate.execute(
                    script,
                    keys,  // KEYS数组
                    String.valueOf(capacity),  // 令牌桶容量(ARGV[1])
                    String.valueOf(System.currentTimeMillis()),  // 当前时间戳(ARGV[2])
                    String.valueOf(rate),  // 令牌填充速率(ARGV[3])
                    String.valueOf(requested)  // 请求令牌数(ARGV[4])
            );

            // 判断结果：1=允许访问，0=被限流
            return result != null && result == 1L;
        } catch (Exception e) {
            // 发生异常时记录日志
            log.error("令牌桶限流异常，key: {}, 异常信息: {}", key, e.getMessage(), e);
            // 默认放行，保证业务可用性(可根据实际需求调整为默认拒绝)
            return true;
        }
    }
}
