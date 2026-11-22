package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.RolePermissionGrant;

/**
 * 角色权限绑定响应视图。
 */
public record RolePermissionVO(Long roleId, Long permissionId, String allowedAction) {

    /**
     * 将领域绑定转换为 API 视图。
     */
    public static RolePermissionVO from(RolePermissionGrant grant) {
        return new RolePermissionVO(
                grant.roleId(), grant.permissionId(), grant.allowedAction().name());
    }
}
