package com.polyauth.oop.interfaces.filter;

import com.polyauth.oop.interfaces.security.AuthInterceptor;
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
 * 请求追踪过滤器，建立 X-Request-Id 与统一响应、审计日志及 HTTP 摘要日志关联。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "polyauth.requestId";
    public static final String HEADER = "X-Request-Id";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final int MAX_LENGTH = 128;
    private static final int MAX_LOG_VALUE_LENGTH = 160;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        org.slf4j.MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                logRequest(request, response, startedAt);
            } finally {
                org.slf4j.MDC.remove("requestId");
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    /**
     * 返回当前请求追踪 ID；非 Web 线程返回空。
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
     * 从 Servlet 请求读取追踪 ID。
     */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR_REQUEST_ID);
        return value == null ? null : value.toString();
    }

    private String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()
                || candidate.length() > MAX_LENGTH
                || !SAFE.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        if (shouldSkipLog(request)) {
            return;
        }
        String uri = sanitizeLogValue(request.getRequestURI());
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
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
                attributeOrDash(request, AuthInterceptor.ATTR_USER_NO),
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
}
