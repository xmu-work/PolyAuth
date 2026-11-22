package com.polyauth.oop.infrastructure.persistence.mapper;

import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RoleGrantStatus;
import com.polyauth.oop.infrastructure.persistence.entity.RoleGrantEntity;

/**
 * 用户角色授权领域对象与数据库模型映射器。
 */
public final class RoleGrantMapper {

    private RoleGrantMapper() {
    }

    /**
     * 将授权数据库模型转换为领域聚合。
     */
    public static RoleGrant toDomain(RoleGrantEntity entity) {
        return RoleGrant.restore(entity.getId(), entity.getUserId(), entity.getRoleId(),
                DomainTime.toInstant(entity.getEffectiveTime()),
                DomainTime.toInstant(entity.getExpireTime()), entity.getGrantBy(),
                entity.getGrantReason(), DomainTime.toInstant(entity.getCreatedAt()),
                DomainTime.toInstant(entity.getUpdatedAt()),
                DomainTime.toInstant(null), RoleGrantStatus.valueOf(entity.getStatus()),
                entity.getVersion());
    }

    /**
     * 将授权领域聚合转换为数据库模型。
     */
    public static RoleGrantEntity toEntity(RoleGrant grant) {
        RoleGrantEntity entity = new RoleGrantEntity();
        entity.setId(grant.id());
        entity.setUserId(grant.userId());
        entity.setRoleId(grant.roleId());
        entity.setEffectiveTime(DomainTime.toLocalDateTime(grant.effectiveTime()));
        entity.setExpireTime(DomainTime.toLocalDateTime(grant.expireTime()));
        entity.setGrantBy(grant.grantBy());
        entity.setGrantReason(grant.reason());
        entity.setStatus(grant.status().name());
        entity.setCreatedAt(DomainTime.toLocalDateTime(grant.createdAt()));
        entity.setUpdatedAt(DomainTime.toLocalDateTime(grant.updatedAt()));
        entity.setVersion(grant.version());
        return entity;
    }
}
