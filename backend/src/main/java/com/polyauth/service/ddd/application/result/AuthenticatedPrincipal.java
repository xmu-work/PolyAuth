package com.polyauth.service.ddd.application.result;

/**
 * 已认证请求上下文。
 *
 * @param userId     本地用户 ID
 * @param systemCode 令牌目标系统
 */
public record AuthenticatedPrincipal(Long userId, String systemCode) {
}
