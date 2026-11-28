package com.polyauth.infrastructure.persistence.ddd.mapper;

import com.polyauth.service.ddd.domain.model.Organization;
import com.polyauth.service.ddd.domain.model.OrganizationStatus;
import com.polyauth.service.ddd.domain.model.OrganizationType;
import com.polyauth.infrastructure.persistence.ddd.entity.OrganizationEntity;

/**
 * 组织领域对象与数据库模型映射器。
 */
public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    /**
     * 将组织数据库模型转换为领域对象。
     */
    public static Organization toDomain(OrganizationEntity entity) {
        return Organization.restore(entity.getId(), entity.getParentId(),
                entity.getAncestors(), entity.getOrgCode(), entity.getOrgName(),
                OrganizationType.valueOf(entity.getOrgType()), entity.getLeaderUserId(),
                entity.getSortNo(), OrganizationStatus.valueOf(entity.getStatus()),
                entity.getIsDeleted() != null && entity.getIsDeleted() != 0,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion(),
                entity.getCreatedBy(), entity.getUpdatedBy());
    }

    /**
     * 将组织领域对象转换为数据库模型。
     */
    public static OrganizationEntity toEntity(Organization organization) {
        OrganizationEntity entity = new OrganizationEntity();
        entity.setId(organization.id());
        entity.setParentId(organization.parentId());
        entity.setAncestors(organization.ancestors());
        entity.setOrgCode(organization.orgCode());
        entity.setOrgName(organization.orgName());
        entity.setOrgType(organization.orgType().name());
        entity.setOrgLevel(organization.level());
        entity.setLeaderUserId(organization.leaderUserId());
        entity.setSortNo(organization.sortNo());
        entity.setStatus(organization.status().name());
        entity.setIsDeleted(organization.isDeleted() ? 1 : 0);
        entity.setCreatedAt(organization.createdAt());
        entity.setUpdatedAt(organization.updatedAt());
        entity.setVersion(organization.version());
        entity.setCreatedBy(organization.createdBy());
        entity.setUpdatedBy(organization.updatedBy());
        return entity;
    }
}
