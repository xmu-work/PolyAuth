package com.polyauth.oop.interfaces.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * OOP 后端外部统一响应包。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    private final int code;
    private final String message;
    private final T data;
    private String requestId;
    private final LocalDateTime timestamp;

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 构造成功响应。
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(0, "success", data);
    }

    /**
     * 构造无数据成功响应。
     */
    public static <T> ApiResult<T> success() {
        return success(null);
    }

    /**
     * 构造失败响应。
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    /**
     * 回填当前请求追踪 ID。
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
