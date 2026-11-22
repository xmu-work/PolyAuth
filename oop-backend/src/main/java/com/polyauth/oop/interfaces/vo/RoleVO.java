package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.Role;

import java.time.LocalDateTime;

/**
 * 角色响应视图。
 */
public record RoleVO(Long id, String roleCode, String roleName, String roleType,
                     Long parentId, String status, String description, Integer isDeleted,
                     LocalDateTime createdAt, LocalDateTime updatedAt,
                     Long createdBy, Long updatedBy) {

    /**
     * 将领域角色转换为 API 视图。
     */
    public static RoleVO from(Role role) {
        return new RoleVO(role.id(), role.roleCode(), role.roleName(),
                role.roleType().name(), role.parentId(), role.status().name(),
                role.description(), 0, role.createdAt(), role.updatedAt(),
                role.createdBy(), role.updatedBy());
    }
}
