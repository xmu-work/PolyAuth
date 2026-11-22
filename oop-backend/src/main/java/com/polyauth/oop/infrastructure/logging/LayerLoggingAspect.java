package com.polyauth.oop.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * OOP 后端统一分层日志切面。
 *
 * <p>只记录层、方法、耗时、成功状态和异常类型，不记录参数、返回值、密码、JWT、
 * Authorization 或完整用户数据。
 */
@Aspect
@Component
public class LayerLoggingAspect {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LayerLoggingAspect.class);

    /**
     * 记录接口层 Controller 调用。
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logInterface(ProceedingJoinPoint joinPoint) throws Throwable {
        return log("interface", joinPoint);
    }

    /**
     * 记录应用用例调用。
     */
    @Around("within(com.polyauth.oop.application.usecase..*)")
    public Object logApplication(ProceedingJoinPoint joinPoint) throws Throwable {
        return log("application", joinPoint);
    }

    /**
     * 记录基础设施适配器调用。
     */
    @Around("within(com.polyauth.oop.infrastructure..*)")
    public Object logInfrastructure(ProceedingJoinPoint joinPoint) throws Throwable {
        return log("infrastructure", joinPoint);
    }

    private Object log(String layer, ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String target = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            LOGGER.debug("layer={} target={} success=true elapsedMs={} requestId={}",
                    layer, target, elapsedMs, requestId());
            return result;
        } catch (Throwable throwable) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            LOGGER.warn("layer={} target={} success=false elapsedMs={} error={} requestId={}",
                    layer, target, elapsedMs, throwable.getClass().getSimpleName(), requestId());
            throw throwable;
        }
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "" : requestId;
    }
}
