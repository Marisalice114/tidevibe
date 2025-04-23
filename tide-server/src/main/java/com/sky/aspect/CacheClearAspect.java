package com.sky.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 自定义切面
 * 实现Redis缓存清理
 */
@Aspect
@Component
@Slf4j
public class CacheClearAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 定义切入点：当商品相关操作执行时清理缓存
     */
    @Pointcut("execution(* com.sky.controller.admin.DishController.save(..)) || " +
            "execution(* com.sky.controller.admin.DishController.delete(..)) || " +
            "execution(* com.sky.controller.admin.DishController.startOrStop(..)) || " +
            "execution(* com.sky.controller.admin.DishController.update(..))")
    public void cacheCleanPointCut() {
    }

    /**
     * 后置通知，在目标方法执行后清理缓存
     */
    @After("cacheCleanPointCut()")
    public void cleanCache(JoinPoint joinPoint) {
        log.info("开始清理商品的Redis缓存数据...");

        // 清理所有以dish_开头的缓存
        Set<String> keys = redisTemplate.keys("dish_*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理了{}个dish_相关的缓存", keys.size());
        }
    }

    /**
     * 为新增商品单独定义切点，只清理特定分类的缓存
     */
    @Pointcut("execution(* com.sky.controller.admin.DishController.save(..))")
    public void savePointCut() {
    }

    /**
     * 针对保存方法单独处理，只清理特定分类的缓存
     * 注意：这个方法需要获取参数中的分类ID，可能需要进一步定制
     */
    /*
    @Around("savePointCut()")
    public Object cleanCategoryCache(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // 先执行目标方法
        Object result = joinPoint.proceed();

        // 获取分类ID并清理特定缓存
        if (args != null && args.length > 0 && args[0] instanceof DishDTO) {
            DishDTO dishDTO = (DishDTO) args[0];
            String key = "dish_" + dishDTO.getCategoryId();
            redisTemplate.delete(key);
            log.info("清理了{}的缓存", key);
        }

        return result;
    }
    */
}
