package com.polyauth.oop.interfaces.vo;

/**
 * 受独立权限保护的敏感用户视图。
 */
public record SensitiveUserVO(Long id, String userNo, String realName, String phone) {
}
