package com.polyauth.service.port.organization;

import com.polyauth.infrastructure.persistence.structured.entity.IamOrg;

import java.util.List;

/**
 * 组织机构用例契约。
 */
public interface OrganizationUseCase {

    IamOrg createOrg(String orgCode, String orgName, String orgType, Integer orgLevel,
                     Long parentId, Integer sortNo);

    IamOrg updateOrg(Long orgId, String orgName, Integer sortNo, Long leaderUserId,
                     String status, Long operatorId);

    List<IamOrg> getOrgTree(Long rootId);

    IamOrg getOrg(Long orgId);

    IamOrg moveOrg(Long orgId, Long newParentId);

    List<IamOrg> getChildren(Long parentId);
}
