package com.polyauth.structured.application.expiration;

/**
 * 到期回收数量结果。
 *
 * @param userRoles 回收的用户角色授权数量
 */
public record ExpirationResult(int userRoles) {
}
