package com.polyauth.service.ddd.adapter.organization;

import com.polyauth.infrastructure.persistence.structured.entity.IamOrg;
import com.polyauth.service.ddd.domain.model.Organization;
import com.polyauth.service.port.organization.OrganizationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组织机构用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddOrganizationService implements OrganizationUseCase {

    private final com.polyauth.service.ddd.application.usecase.OrganizationUseCase useCase;

    @Override
    public IamOrg createOrg(String orgCode, String orgName, String orgType, Integer orgLevel,
                            Long parentId, Integer sortNo) {
        return toEntity(useCase.create(orgCode, orgName, orgType, orgLevel, parentId, sortNo, null));
    }

    @Override
    public IamOrg updateOrg(Long orgId, String orgName, Integer sortNo, Long leaderUserId,
                            String status, Long operatorId) {
        return toEntity(useCase.update(orgId, orgName, sortNo, leaderUserId, status, operatorId));
    }

    @Override
    public List<IamOrg> getOrgTree(Long rootId) {
        return useCase.tree(rootId).stream().map(this::toEntity).toList();
    }

    @Override
    public IamOrg getOrg(Long orgId) {
        return toEntity(useCase.get(orgId));
    }

    @Override
    public IamOrg moveOrg(Long orgId, Long newParentId) {
        return toEntity(useCase.move(orgId, newParentId, null));
    }

    @Override
    public List<IamOrg> getChildren(Long parentId) {
        return useCase.children(parentId).stream().map(this::toEntity).toList();
    }

    private IamOrg toEntity(Organization org) {
        IamOrg entity = new IamOrg();
        entity.setId(org.id());
        entity.setParentId(org.parentId());
        entity.setAncestors(org.ancestors());
        entity.setOrgCode(org.orgCode());
        entity.setOrgName(org.orgName());
        entity.setOrgType(org.orgType() == null ? null : org.orgType().name());
        entity.setOrgLevel(org.level());
        entity.setLeaderUserId(org.leaderUserId());
        entity.setSortNo(org.sortNo());
        entity.setStatus(org.status() == null ? null : org.status().name());
        entity.setIsDeleted(org.isDeleted() ? 1 : 0);
        entity.setCreatedAt(org.createdAt());
        entity.setUpdatedAt(org.updatedAt());
        entity.setVersion(org.version());
        entity.setCreatedBy(org.createdBy());
        entity.setUpdatedBy(org.updatedBy());
        return entity;
    }
}
