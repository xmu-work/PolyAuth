package com.polyauth.oop.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OOP 后端统一分层日志切面。
 *
 * <p>只记录层、方法、耗时、成功状态和异常类型，不记录参数、返回值、密码、JWT、
 * Authorization 或完整用户数据；Logger 按目标业务类解析，避免与 MDC requestId 重复输出。
 */
@Aspect
@Component
public class LayerLoggingAspect {

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
    @Around("within(com.polyauth.oop.infrastructure..*) && !within(com.polyauth.oop.infrastructure.logging..*)")
    public Object logInfrastructure(ProceedingJoinPoint joinPoint) throws Throwable {
        return log("infrastructure", joinPoint);
    }

    private Object log(String layer, ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetType = resolveTargetType(joinPoint);
        Logger logger = LoggerFactory.getLogger(targetType);
        long startedAt = System.nanoTime();
        String target = targetType.getSimpleName()
                + "." + joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            if (logger.isDebugEnabled()) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                logger.debug("layer={} target={} success=true elapsedMs={}",
                        layer, target, elapsedMs);
            }
            return result;
        } catch (Throwable throwable) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.warn("layer={} target={} success=false elapsedMs={} error={}",
                    layer, target, elapsedMs, throwable.getClass().getSimpleName());
            throw throwable;
        }
    }

    private Class<?> resolveTargetType(ProceedingJoinPoint joinPoint) {
        Class<?> declaringType = joinPoint.getSignature().getDeclaringType();
        if (declaringType != null && declaringType.getName().startsWith("com.polyauth.oop.")) {
            return declaringType;
        }
        Object target = joinPoint.getTarget();
        if (target != null && target.getClass().getName().startsWith("com.polyauth.oop.")) {
            return target.getClass();
        }
        Object proxy = joinPoint.getThis();
        if (proxy != null) {
            for (Class<?> iface : proxy.getClass().getInterfaces()) {
                if (iface.getName().startsWith("com.polyauth.oop.")) {
                    return iface;
                }
            }
        }
        return LayerLoggingAspect.class;
    }
}
