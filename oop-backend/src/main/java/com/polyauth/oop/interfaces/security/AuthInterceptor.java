package com.polyauth.oop.interfaces.security;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.application.result.AuthenticatedPrincipal;
import com.polyauth.oop.application.usecase.AuthenticationUseCase;
import com.polyauth.oop.application.usecase.OperationAccessUseCase;
import com.polyauth.oop.interfaces.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * OOP 后端认证、默认拒绝鉴权与请求审计拦截器。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "polyauth.userId";
    public static final String ATTR_SYSTEM_CODE = "polyauth.systemCode";
    public static final String ATTR_USER_NO = "polyauth.userNo";
    public static final String ATTR_AUDITED = "polyauth.audited";

    private static final Set<String> INFRA_PREFIXES =
            Set.of("/actuator", "/druid", "/error", "/favicon.ico");
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)(^|&)(password|passwd|pwd|token|accessToken|authorization|secret|"
                    + "oldPassword|newPassword|realName|name|phone|mobile|email|idCard)"
                    + "=([^&]*)");

    private final AuthenticationUseCase authenticationUseCase;
    private final OperationAccessUseCase operationAccessUseCase;
    private final AuditTrail auditTrail;

    /**
     * 注入认证、API 权限与审计用例。
     */
    public AuthInterceptor(AuthenticationUseCase authenticationUseCase,
                           OperationAccessUseCase operationAccessUseCase,
                           AuditTrail auditTrail) {
        this.authenticationUseCase = authenticationUseCase;
        this.operationAccessUseCase = operationAccessUseCase;
        this.auditTrail = auditTrail;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (CorsUtils.isPreFlightRequest(request)
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !(handler instanceof HandlerMethod)) {
            return true;
        }
        try {
            String token = bearerToken(request.getHeader("Authorization"));
            AuthenticatedPrincipal principal = authenticationUseCase.authenticate(token);
            request.setAttribute(ATTR_USER_ID, principal.userId());
            request.setAttribute(ATTR_SYSTEM_CODE, principal.systemCode());
            request.setAttribute(ATTR_USER_NO, authenticationUseCase.userNo(principal.userId()));

            String path = relativePath(request);
            if (isAuthPath(path) || isInfrastructure(path)) {
                return true;
            }
            String pattern = (String) request.getAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) {
                throw new UseCaseException(
                        ApplicationErrorCode.NO_FUNCTION_PERM, "无法定位接口登记，默认拒绝");
            }
            operationAccessUseCase.requireRequest(
                    principal.userId(), pattern, request.getMethod());
            return true;
        } catch (RuntimeException exception) {
            recordAudit(request, response, exception);
            throw exception;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (CorsUtils.isPreFlightRequest(request)
                || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return;
        }
        if (handler instanceof HandlerMethod) {
            recordAudit(request, response, exception);
        }
    }

    private void recordAudit(HttpServletRequest request, HttpServletResponse response,
                             Exception exception) {
        String path = relativePath(request);
        if (isInfrastructure(path) || Boolean.TRUE.equals(request.getAttribute(ATTR_AUDITED))) {
            return;
        }
        request.setAttribute(ATTR_AUDITED, true);
        Long userId = (Long) request.getAttribute(ATTR_USER_ID);
        String userNo = (String) request.getAttribute(ATTR_USER_NO);
        String systemCode = (String) request.getAttribute(ATTR_SYSTEM_CODE);
        boolean success = exception == null && response.getStatus() < 400;
        auditTrail.recordRequest(userId, userNo, systemCode,
                request.getMethod() + " " + path, path, request.getRemoteAddr(),
                success, exception == null ? null : exception.getClass().getSimpleName(),
                RequestIdFilter.requestId(request));
    }

    private String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return "";
        }
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/auth/");
    }

    private boolean isInfrastructure(String path) {
        return INFRA_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 从 Authorization 头中提取 Bearer 令牌。
     */
    public static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            throw new UseCaseException(
                    ApplicationErrorCode.UNAUTHENTICATED, "请先登录");
        }
        return authorization.substring(7);
    }
}
