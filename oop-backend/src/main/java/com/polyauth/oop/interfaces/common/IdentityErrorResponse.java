package com.polyauth.oop.interfaces.common;

/**
 * 统一身份接口字符串错误响应。
 */
public record IdentityErrorResponse(String code, String message) {
}
