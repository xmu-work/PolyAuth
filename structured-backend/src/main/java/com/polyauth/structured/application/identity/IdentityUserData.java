package com.polyauth.structured.application.identity;

/**
 * 统一身份用户摘要。
 *
 * @param userId 统一身份用户标识
 * @param no     学号或工号
 * @param name   姓名
 */
public record IdentityUserData(String userId, String no, String name) {
}
