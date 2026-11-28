package com.polyauth.infrastructure.persistence.ddd.mapper;

import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleStatus;
import com.polyauth.service.ddd.domain.model.RoleType;
import com.polyauth.infrastructure.persistence.ddd.entity.RoleEntity;

import java.util.List;

/**
 * 角色数据库模型与领域聚合显式映射器。
 */
public final class RoleMapper {

    private RoleMapper() {
    }

    /**
     * 将数据库角色转换为领域聚合。
     */
    public static Role toDomain(RoleEntity entity, List<Long> parentIds) {
        return Role.restore(entity.getId(), entity.getRoleCode(), entity.getRoleName(),
                RoleType.valueOf(entity.getRoleType()), parentIds,
                RoleStatus.valueOf(entity.getStatus()), entity.getDescription(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion(),
                entity.getCreatedBy(), entity.getUpdatedBy());
    }

    /**
     * 兼容只持有单父角色列时的映射。
     */
    public static Role toDomain(RoleEntity entity) {
        return toDomain(entity, entity.getParentId() == null
                ? List.of() : List.of(entity.getParentId()));
    }

    /**
     * 将领域角色转换为数据库模型。
     */
    public static RoleEntity toEntity(Role role) {
        RoleEntity entity = new RoleEntity();
        entity.setId(role.id());
        entity.setRoleCode(role.roleCode());
        entity.setRoleName(role.roleName());
        entity.setRoleType(role.roleType().name());
        entity.setParentId(role.parentId());
        entity.setStatus(role.status().name());
        entity.setDescription(role.description());
        entity.setIsDeleted(0);
        entity.setCreatedAt(role.createdAt());
        entity.setUpdatedAt(role.updatedAt());
        entity.setVersion(role.version());
        entity.setCreatedBy(role.createdBy());
        entity.setUpdatedBy(role.updatedBy());
        return entity;
    }
}
