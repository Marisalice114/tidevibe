package com.sky.aspect;


import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面
 * 实现公共字段自动填充
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    //指定切入点,同时还要满足方法上面加入了AutoFill注解
    public void autoFillPointCut(){

    }

    /**
     * 前置通知
     * 在通知中进行公共字段的赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行公共字段的自动填充");

        //获取拦截到的当前方法的数据库操作类型

        //singature类被一系列类继承了，可以找到最合适的那个类，将其向下转型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);//获得方法上的注解对象
        OperationType operationType = autoFill.value();//获取数据库操作类型

        //获取到方法参数（实体对象）

        Object[] args = joinPoint.getArgs();//约定第一个参数为实体对象
        if (args == null || args.length == 0){
            return; //防止出现异常情况
        }
        Object entity = args[0];

        //准备赋值的数据

        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //根据当前不同的操作类型，为对应的属性通过反射来赋值

        switch (operationType){
            case INSERT:
                // 为4个公共字段赋值
                // 获得set方法
                try {
                    Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                    Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                    //这里是根据参数的类型来决定后面是类型.class
                    Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                    Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);

                    //通过反射为对象属性赋值
                    setCreateTime.invoke(entity,now);
                    setUpdateTime.invoke(entity,now);
                    setCreateUser.invoke(entity,currentId);
                    setUpdateUser.invoke(entity,currentId);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            case UPDATE:
                // 为2个公共字段赋值
                try {
                    Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                    //这里是根据参数的类型来决定后面是类型.class
                    Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                    //通过反射为对象属性赋值
                    setUpdateTime.invoke(entity,now);
                    setUpdateUser.invoke(entity,currentId);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }

    }

}
