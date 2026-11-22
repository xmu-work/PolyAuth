package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.PermissionNode;

import java.time.LocalDateTime;

/**
 * 前端权限节点响应视图。
 */
public record PermissionVO(
        Long id,
        Long systemId,
        Long parentId,
        String permissionCode,
        String permissionName,
        String permissionType,
        String routePath,
        String action,
        Integer sortNo,
        String status,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PermissionVO from(PermissionNode node) {
        return new PermissionVO(node.id(), node.systemId(), node.parentId(),
                node.permissionCode(), node.permissionName(), node.permissionType(),
                node.routePath(), node.action().name(), node.sortNo(), node.status(),
                node.deleted() ? 1 : 0, node.createdAt(), node.updatedAt());
    }
}
