package com.polyauth.oop.domain.model;

/**
 * 角色到前端权限的授予事实。
 *
 * @param roleId        角色 ID
 * @param permissionId  权限 ID
 * @param allowedAction 已授予的操作为
 */
public record RolePermissionGrant(Long roleId, Long permissionId, OperationAction allowedAction) {
}
