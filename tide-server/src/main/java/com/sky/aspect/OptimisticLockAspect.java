package com.sky.aspect;

import com.sky.annotation.OptimisticLock;
import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


@Aspect
@Component
@Slf4j
public class OptimisticLockAspect {
    /**
     * 乐观锁切面类
     */
    @Autowired
    private OrderMapper orderMapper;

    @Around("@annotation(com.sky.annotation.OptimisticLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解信息
        OptimisticLock optimisticLock = method.getAnnotation(OptimisticLock.class);
        int maxRetries = optimisticLock.maxRetries();
        String entityParamName = optimisticLock.entityParam();

        // 获取参数
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();
        Orders orders = null;
        Long orderId = null;

        // 查找订单实体或ID参数
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(entityParamName) && args[i] instanceof Orders) {
                // 方法参数中直接有Orders对象
                orders = (Orders) args[i];
                break;
            } else if (parameterNames[i].equals("id") && args[i] instanceof Long) {
                // 方法参数中只有订单ID，需要额外查询Orders对象
                orderId = (Long) args[i];
            }
        }

        // 如果没有找到订单对象，但有订单ID，则查询订单
        if (orders == null && orderId != null) {
            orders = orderMapper.getById(orderId);
            if (orders == null) {
                throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
            }
        }

        if (orders == null) {
            // 如果仍然没有订单对象，直接执行原方法
            return joinPoint.proceed();
        }

        // 乐观锁重试逻辑
        boolean updated = false;
        int retryCount = 0;
        Object result = null;

        while (!updated && retryCount < maxRetries) {
            // 记录当前版本号
            Integer currentVersion = orders.getVersion();

            try {
                // 执行原方法
                result = joinPoint.proceed();

                // 尝试使用版本号更新
                int rows = orderMapper.updateWithVersion(orders);

                if (rows == 1) {
                    // 更新成功
                    updated = true;
                    log.info("订单:{} 操作成功, 版本号:{}", orders.getId(), currentVersion);
                } else {
                    // 版本号冲突
                    retryCount++;
                    log.warn("乐观锁冲突，订单:{} 操作失败, 重试次数:{}", orders.getId(), retryCount);

                    // 重新获取最新数据
                    orders = orderMapper.getById(orders.getId());
                    if (orders == null) {
                        throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
                    }

                    // 更新参数中的订单对象
                    // 重试时需要使用最新的订单对象，确保后续操作基于最新数据
                    for (int i = 0; i < parameterNames.length; i++) {
                        if (parameterNames[i].equals(entityParamName) && args[i] instanceof Orders) {
                            args[i] = orders;
                            break;
                        }
                    }
                }
            } catch (OrderBusinessException e) {
                // 直接抛出业务异常
                throw e;
            } catch (Throwable e) {
                // 其他异常进行重试
                retryCount++;
                log.error("执行方法异常，订单:{} 操作失败, 重试次数:{}", orders.getId(), retryCount, e);

                if (retryCount >= maxRetries) {
                    throw e;
                }

                // 重新获取最新数据
                orders = orderMapper.getById(orders.getId());
                if (orders == null) {
                    throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
                }
            }
        }

        if (!updated) {
            throw new OrderBusinessException(MessageConstant.ORDER_OPERATION_FAIL);
        }

        return result;
    }
}
