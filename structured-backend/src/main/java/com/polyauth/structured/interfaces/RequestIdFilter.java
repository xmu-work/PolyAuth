package com.polyauth.structured.interfaces;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求追踪过滤器：把前端传入的 {@code X-Request-Id}（缺省时新生成）写入请求属性并回写响应头，
 * 供统一响应包 {@code Result.requestId} 回填与日志关联，并在请求结束时输出 HTTP 摘要日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "iam.requestId";
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final int MAX_LOG_VALUE_LENGTH = 160;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        org.slf4j.MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                logRequest(request, response, startedAt);
            } finally {
                org.slf4j.MDC.remove(MDC_KEY);
            }
        }
    }

    /**
     * 当前请求的追踪 ID，供非 Web 层（如审计）使用；非请求线程返回 null。
     */
    public static String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(ATTR_REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
        return value == null ? null : value.toString();
    }

    /**
     * 直接读取当前 Servlet 请求上的追踪 ID，供 DispatcherServlet 初始化前的过滤器使用。
     */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR_REQUEST_ID);
        return value == null ? null : value.toString();
    }

    private static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()
                || candidate.length() > MAX_REQUEST_ID_LENGTH
                || !SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        if (shouldSkipLog(request)) {
            return;
        }
        String uri = sanitizeLogValue(request.getRequestURI());
        long elapsedMs = elapsedMillis(startedAt);
        if (CorsUtils.isPreFlightRequest(request) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("http method={} uri={} status={} elapsedMs={}",
                        request.getMethod(), uri, response.getStatus(), elapsedMs);
            }
            return;
        }
        LOGGER.info("http method={} uri={} status={} elapsedMs={} user={} system={}",
                sanitizeLogValue(request.getMethod()),
                uri,
                response.getStatus(),
                elapsedMs,
                attributeOrDash(request, AuthInterceptor.ATTR_USERNAME),
                attributeOrDash(request, AuthInterceptor.ATTR_SYSTEM_CODE));
    }

    private boolean shouldSkipLog(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String path = relativePath(request);
        return path.startsWith("/actuator")
                || path.startsWith("/api/v1/actuator")
                || path.startsWith("/favicon.ico")
                || uri.endsWith("/favicon.ico");
    }

    private String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String attributeOrDash(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        return value == null ? "-" : sanitizeLogValue(value.toString());
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_').replace('\t', ' ').trim();
        if (sanitized.isEmpty()) {
            return "-";
        }
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
