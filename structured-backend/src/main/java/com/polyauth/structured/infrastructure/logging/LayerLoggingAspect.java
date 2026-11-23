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

    private static final Logger LOGGER = LoggerFactory.getLogger(LayerLoggingAspect.class);

    /**
     * 记录分层方法开始、正常完成耗时或异常类型。
     *
     * @param joinPoint 被代理方法
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("execution(* com.polyauth.structured.interfaces..*Controller.*(..))"
            + " || execution(* com.polyauth.structured.application..*(..))"
            + " || execution(* com.polyauth.structured.infrastructure..*(..))")
    public Object logLayer(ProceedingJoinPoint joinPoint) throws Throwable {
        String layer = layerOf(joinPoint.getTarget().getClass().getName());
        String type = joinPoint.getTarget().getClass().getSimpleName();
        String method = joinPoint.getSignature().getName();
        long started = System.nanoTime();
        LOGGER.debug("layer={} class={} method={} phase=start", layer, type, method);
        try {
            Object result = joinPoint.proceed();
            LOGGER.debug("layer={} class={} method={} phase=complete elapsedMs={}",
                    layer, type, method, elapsedMillis(started));
            return result;
        } catch (Throwable exception) {
            LOGGER.warn("layer={} class={} method={} phase=exception elapsedMs={} type={}",
                    layer, type, method, elapsedMillis(started), exception.getClass().getSimpleName());
            throw exception;
        }
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
