package com.polyauth.structured.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * API、Service 与 Infrastructure 的统一分层日志切面。
 *
 * <p>只记录层名、类名、方法名、耗时和异常类型，不记录参数、返回值、令牌、
 * 密码、Authorization 头或完整异常消息。
 */
@Aspect
@Component
public class LayerLoggingAspect {

    /**
     * 记录分层方法开始、正常完成耗时或异常类型，按目标业务类解析 Logger 以便各层级别独立生效。
     *
     * @param joinPoint 被代理方法
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("(execution(* com.polyauth.structured.interfaces..*Controller.*(..))"
            + " || execution(* com.polyauth.structured.application..*(..))"
            + " || execution(* com.polyauth.structured.infrastructure..*(..)))"
            + " && !within(com.polyauth.structured.infrastructure.logging..*)"
            + " && !within(com.polyauth.structured.infrastructure.scheduling..*)"
            + " && !within(com.polyauth.structured.application.expiration..*)")
    public Object logLayer(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetType = resolveTargetType(joinPoint);
        Logger logger = LoggerFactory.getLogger(targetType);
        String layer = layerOf(targetType.getName());
        String type = targetType.getSimpleName();
        String method = joinPoint.getSignature().getName();
        boolean schedulingThread = Thread.currentThread().getName().startsWith("scheduling-");
        long started = System.nanoTime();
        if (!schedulingThread && logger.isDebugEnabled()) {
            logger.debug("layer={} class={} method={} phase=start", layer, type, method);
        }
        try {
            Object result = joinPoint.proceed();
            if (!schedulingThread && logger.isDebugEnabled()) {
                logger.debug("layer={} class={} method={} phase=complete elapsedMs={}",
                        layer, type, method, elapsedMillis(started));
            }
            return result;
        } catch (Throwable exception) {
            logger.warn("layer={} class={} method={} phase=exception elapsedMs={} type={}",
                    layer, type, method, elapsedMillis(started), exception.getClass().getSimpleName());
            throw exception;
        }
    }

    /**
     * 解析实际业务类型，优先还原 Spring Data JPA 代理背后的业务接口。
     */
    private Class<?> resolveTargetType(ProceedingJoinPoint joinPoint) {
        Class<?> declaringType = joinPoint.getSignature().getDeclaringType();
        if (declaringType != null && declaringType.getName().startsWith("com.polyauth.structured.")) {
            return declaringType;
        }
        Object target = joinPoint.getTarget();
        if (target != null && target.getClass().getName().startsWith("com.polyauth.structured.")) {
            return target.getClass();
        }
        Object proxy = joinPoint.getThis();
        if (proxy != null) {
            for (Class<?> iface : proxy.getClass().getInterfaces()) {
                if (iface.getName().startsWith("com.polyauth.structured.")) {
                    return iface;
                }
            }
        }
        return LayerLoggingAspect.class;
    }

    /**
     * 从目标类包名判断业务层名称。
     */
    private String layerOf(String className) {
        if (className.startsWith("com.polyauth.structured.interfaces")) {
            return "api";
        }
        if (className.startsWith("com.polyauth.structured.application")) {
            return "service";
        }
        return "infrastructure";
    }

    /**
     * 计算相对开始时间的毫秒耗时。
     */
    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
