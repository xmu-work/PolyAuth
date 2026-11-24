package com.polyauth.structured.interfaces;

import com.polyauth.structured.application.api.ApiService;
import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.auth.AuthenticatedSession;
import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.persistence.entity.IamApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 认证与鉴权拦截器。
 *
 * <p>默认拒绝（fail-closed）：除显式白名单外，请求对应的 API（映射模板 + HTTP 方法）
 * 必须已登记为 API 且映射到前端权限，当前用户拥有该权限时才放行。
 *
 * <p>流程：取 Bearer 令牌 → 认证得到用户 ID（失败 401）→ 放行白名单 →
 * 查找 API（查不到即拒绝）→ 取得 API 所属前端权限 → 判定用户是否拥有该权限（无权 403）。
 *
 * <p>审计：写操作在 {@link #afterCompletion} 中按服务端上下文记录操作人，客户端无法伪造归属。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 请求属性：当前登录用户 ID */
    public static final String ATTR_USER_ID = "iam.userId";
    /** 请求属性：JWT 目标业务系统编码。 */
    public static final String ATTR_SYSTEM_CODE = "iam.systemCode";
    /** 请求属性：当前用户名快照，仅用于审计。 */
    public static final String ATTR_USERNAME = "iam.username";
    /** 请求属性：是否已写入审计，避免 preHandle 失败与 afterCompletion 重复。 */
    public static final String ATTR_AUDITED = "iam.audited";

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)(^|&)(password|passwd|pwd|token|accessToken|refreshToken|authorization|secret|"
                    + "oldPassword|newPassword|realName|name|phone|mobile|email|idCard)"
                    + "=([^&]*)");

    /** 开放 API 前缀：见 {@code OpenApiAuthInterceptor}，在此不做管理端功能点校验 */
    private static final String OPEN_API_PREFIX = "/open/";

    /** 非管理端基础设施路径（监控、健康检查、错误页），不参与功能点校验与审计 */
    private static final Set<String> INFRA_PREFIXES = Set.of("/actuator", "/druid", "/error", "/favicon.ico");

    private final AuthService authService;
    private final ApiService apiService;
    private final DecisionService decisionService;
    private final LogService logService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !(handler instanceof HandlerMethod)) {
            return true;
        }

        try {
            String token = bearerToken(request.getHeader("Authorization"));
            AuthenticatedSession session = authService.authenticateSession(token);
            request.setAttribute(ATTR_USER_ID, session.userId());
            request.setAttribute(ATTR_USERNAME, authService.getUserNo(session.userId()));
            request.setAttribute(ATTR_SYSTEM_CODE, session.systemCode());

            String path = relativePath(request);
            if (isLoginOnly(path) || isInfra(path)) {
                return true;
            }

            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            IamApi api = apiService.findByRequest(pattern, request.getMethod())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NO_FUNCTION_PERM,
                            "接口未登记为 API，默认拒绝: " + request.getMethod() + " " + path));
            if (api.getPermissionId() == null
                    || !decisionService.hasPermission(
                            session.userId(), AuthService.ADMIN_SYSTEM_CODE, api.getPermissionId())) {
                throw new BusinessException(ErrorCode.NO_FUNCTION_PERM, "无功能权限: " + api.getApiName());
            }
            return true;
        } catch (RuntimeException exception) {
            recordAudit(request, response, exception);
            throw exception;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (CorsUtils.isPreFlightRequest(request)
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !(handler instanceof HandlerMethod)) {
            return;
        }
        recordAudit(request, response, ex);
    }

    private String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return "";
        }
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private boolean isLoginOnly(String path) {
        return path.startsWith(OPEN_API_PREFIX) || path.startsWith("/auth/");
    }

    private boolean isInfra(String path) {
        return INFRA_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 写入不含敏感参数值的请求审计日志。
     */
    private void recordAudit(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        String path = relativePath(request);
        if (isInfra(path) || Boolean.TRUE.equals(request.getAttribute(ATTR_AUDITED))) {
            return;
        }
        request.setAttribute(ATTR_AUDITED, true);
        Long userId = (Long) request.getAttribute(ATTR_USER_ID);
        String username = (String) request.getAttribute(ATTR_USERNAME);
        String systemCode = (String) request.getAttribute(ATTR_SYSTEM_CODE);
        boolean success = exception == null && response.getStatus() < 400;
        logService.record(userId, username,
                systemCode == null ? AuthService.ADMIN_SYSTEM_CODE : systemCode,
                request.getMethod() + " " + path,
                path,
                sanitizeQuery(request.getQueryString()),
                request.getRemoteAddr(),
                success,
                exception == null ? null : exception.getClass().getSimpleName(),
                RequestIdFilter.requestId(request));
    }

    /**
     * 对查询串中的密码、令牌、密钥等敏感值做不可逆占位替换。
     */
    private String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        return SENSITIVE_QUERY.matcher(queryString).replaceAll("$1$2=***");
    }

    /**
     * 从 Authorization 请求头中取出 Bearer 令牌，缺失时抛出 UNAUTHENTICATED。
     */
    public static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return authorization.substring(7);
    }
}
