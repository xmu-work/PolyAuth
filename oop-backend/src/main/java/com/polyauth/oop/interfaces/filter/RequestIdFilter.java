package com.polyauth.oop.interfaces.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求追踪过滤器，建立 X-Request-Id 与统一响应、审计日志关联。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "polyauth.requestId";
    public static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        org.slf4j.MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove("requestId");
            RequestContextHolder.resetRequestAttributes();
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
}
