package com.polyauth.oop.interfaces.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.port.IdempotencyStore;
import com.polyauth.oop.interfaces.common.ApiResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 写请求幂等过滤器，保持与结构化后端一致的 1004 冲突语义。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS =
            Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final int MAX_KEY_LENGTH = 128;
    private static final Pattern SAFE_KEY =
            Pattern.compile("[A-Za-z0-9._:-]+");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    @Value("${idempotency.ttl:600}")
    private long ttlSeconds;

    @Value("${idempotency.max-body-bytes:1048576}")
    private int maxBodyBytes;

    /**
     * 注入幂等存储与 JSON 序列化器。
     */
    public IdempotencyFilter(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()
                || !MUTATING_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (key.length() > MAX_KEY_LENGTH || !SAFE_KEY.matcher(key).matches()) {
            writeError(response, HttpStatus.BAD_REQUEST,
                    ApplicationErrorCode.PARAM_INVALID,
                    "Idempotency-Key 长度或格式非法", request);
            return;
        }
        Map<String, String[]> parameters = request.getParameterMap();
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE,
                    ApplicationErrorCode.PARAM_INVALID,
                    "幂等请求体超过大小上限", request);
            return;
        }
        HttpServletRequest wrapped =
                new CachedBodyHttpServletRequest(request, body);
        String fingerprint = fingerprint(wrapped, parameters, body);
        IdempotencyStore.Status status =
                store.acquire(key, fingerprint, ttlSeconds);
        switch (status) {
            case CONFLICT -> {
                writeError(response, HttpStatus.CONFLICT,
                        ApplicationErrorCode.IDEMPOTENCY_CONFLICT,
                        "幂等键已用于不同的请求", request);
                return;
            }
            case IN_PROGRESS -> {
                writeError(response, HttpStatus.CONFLICT,
                        ApplicationErrorCode.IDEMPOTENCY_CONFLICT,
                        "相同请求正在处理中，请稍后重试", request);
                return;
            }
            case COMPLETED -> {
                replay(response, store.getResponse(key), request);
                return;
            }
            default -> {
            }
        }
        ContentCachingResponseWrapper wrapper =
                new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrapped, wrapper);
            if (response.getStatus() < 400) {
                store.complete(key, response.getStatus(),
                        new String(wrapper.getContentAsByteArray(),
                                StandardCharsets.UTF_8), ttlSeconds);
            } else {
                store.release(key);
            }
        } catch (IOException | ServletException | RuntimeException exception) {
            store.release(key);
            throw exception;
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    private String fingerprint(HttpServletRequest request,
                               Map<String, String[]> parameters, byte[] body) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(request.getMethod()).append('\n')
                .append(request.getRequestURI()).append('\n')
                .append(request.getContentType() == null
                        ? "" : request.getContentType()).append('\n')
                .append(sha256(request.getHeader("Authorization") == null
                        ? "" : request.getHeader("Authorization"))).append('\n');
        parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    canonical.append(entry.getKey()).append('=');
                    String[] values = entry.getValue() == null
                            ? new String[0] : entry.getValue();
                    Arrays.stream(values).sorted()
                            .forEach(value -> canonical.append(value).append(','));
                    canonical.append(';');
                });
        canonical.append('\n').append(sha256(body));
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            ApplicationErrorCode code, String message,
                            HttpServletRequest request)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResult<Void> result = ApiResult.fail(
                code.code(), message);
        result.setRequestId(RequestIdFilter.requestId(request));
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private void replay(HttpServletResponse response, String stored,
                        HttpServletRequest request) throws IOException {
        int status = HttpStatus.OK.value();
        String body = "";
        if (stored != null) {
            int separator = stored.indexOf('\n');
            if (separator >= 0) {
                status = Integer.parseInt(stored.substring(0, separator));
                body = stored.substring(separator + 1);
            } else {
                body = stored;
            }
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(withRequestId(body,
                RequestIdFilter.requestId(request)));
    }

    private String withRequestId(String body, String requestId)
            throws IOException {
        if (body == null || body.isBlank() || requestId == null) {
            return body;
        }
        try {
            if (objectMapper.readTree(body) instanceof ObjectNode result) {
                result.put("requestId", requestId);
                return objectMapper.writeValueAsString(result);
            }
        } catch (IOException ignored) {
            // 非 JSON 响应保持原样。
        }
        return body;
    }
}
