package com.polyauth.service.ddd.domain.model;

import java.time.LocalDateTime;

/**
 * 角色到前端权限的授予事实。
 *
 * @param roleId        角色 ID
 * @param permissionId  权限 ID
 * @param allowedAction 已授予的操作为
 */
public record RolePermissionGrant(
        Long id,
        Long roleId,
        Long permissionId,
        OperationAction allowedAction,
        LocalDateTime createdAt,
        Long createdBy) {

    /**
     * 兼容只依赖授权事实的纯计算调用。
     */
    public RolePermissionGrant(Long roleId, Long permissionId,
                               OperationAction allowedAction) {
        this(null, roleId, permissionId, allowedAction, null, null);
    }
}
