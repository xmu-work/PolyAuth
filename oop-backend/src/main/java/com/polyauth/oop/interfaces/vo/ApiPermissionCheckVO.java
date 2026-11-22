package com.polyauth.oop.interfaces.vo;

/**
 * operationId 权限检查响应。
 */
public record ApiPermissionCheckVO(String operationId, boolean allowed) {
}
