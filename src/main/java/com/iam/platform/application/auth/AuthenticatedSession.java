package com.iam.platform.application.auth;

/**
 * 已认证会话上下文。
 *
 * @param userId     本地用户 ID
 * @param systemCode 当前访问的目标系统
 */
public record AuthenticatedSession(Long userId, String systemCode) {
}
