package com.sky.aspect;

import com.sky.config.RateLimit;
import com.sky.context.BaseContext;
import com.sky.exception.OrderBusinessException;
import com.sky.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Slf4j
@Component
public class RateLimitAspect {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Around("@annotation(com.sky.config.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        //获取限流注解
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        // 构建限流key
        String limitKey = buildLimitKey(rateLimit.key(), method);
        boolean allowed = false;

        // 根据限流类型选择不同的限流策略
        if (rateLimit.type() == 0) {
            // 固定窗口限流
            allowed = rateLimiterService.isAllowed(
                    limitKey,
                    rateLimit.windowSeconds(),
                    rateLimit.limit()
            );
        } else {
            // 令牌桶限流
            allowed = rateLimiterService.isAllowedByTokenBucket(
                    limitKey,
                    rateLimit.capacity(),
                    rateLimit.rate(),
                    rateLimit.requested()
            );
        }

        if (!allowed) {
            log.warn("接口访问频率超限: {}", limitKey);
            throw new OrderBusinessException(rateLimit.message());
        }

        return joinPoint.proceed();
    }

    /**
     * 构建限流的key
     * @param key
     * @param method
     * @return
     */
    private String buildLimitKey(String key, Method method) {
        StringBuilder limitKey = new StringBuilder("rate_limit:");

        // 如果没有指定key，就用方法全限定名
        if (key.isEmpty()) {
            limitKey.append(method.getDeclaringClass().getName())
                    .append(".")
                    .append(method.getName());
        } else {
            limitKey.append(key);
        }

        // 支持按用户ID限流
        if (key.contains("{userId}")) {
            Long userId = BaseContext.getCurrentId();
            limitKey = new StringBuilder(limitKey.toString().replace("{userId}", userId != null ? userId.toString() : "anonymous"));
        }

        // 支持按IP限流
        if (key.contains("{ip}")) {
            String ip = getIpAddress();
            limitKey = new StringBuilder(limitKey.toString().replace("{ip}", ip));
        }

        return limitKey.toString();
    }

    /**
     * 获取请求IP地址
     */
    private String getIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.error("获取IP地址异常", e);
        }
        return "unknown";
    }
}
/**
 * 当一个带有 @RateLimit 注解的方法被调用时：
 * 切面拦截该方法调用
 * 获取注解中的限流参数
 * 构建限流键（可能包含用户ID或IP）
 * 根据注解中的类型参数选择限流算法
 * 使用限流服务检查是否允许请求
 * 如果允许，执行原方法并返回结果
 * 如果不允许，抛出业务异常并附带自定义消息
 */

/**
 * 这里支持多种限流策略
 * 1.方法级限流
 * 2.用户级限流
 * 3.IP级限流
 * 4.组合限流策略
 * 5.动态限流策略
 * 6.自定义限流策略
 */
