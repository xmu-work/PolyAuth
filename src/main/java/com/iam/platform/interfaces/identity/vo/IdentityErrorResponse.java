package com.iam.platform.interfaces.identity.vo;

/**
 * 统一身份外部契约错误响应。
 *
 * @param code    字符串业务错误码
 * @param message 可直接展示的中文错误信息
 */
public record IdentityErrorResponse(String code, String message) {
}
