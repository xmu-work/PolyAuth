package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.Organization;

import java.time.LocalDateTime;

/**
 * 组织节点响应视图。
 */
public record OrganizationVO(
        Long id,
        Long parentId,
        String ancestors,
        String orgCode,
        String orgName,
        String orgType,
        Integer orgLevel,
        Long leaderUserId,
        Integer sortNo,
        String status,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将组织领域对象转换为 API 视图。
     */
    public static OrganizationVO from(Organization organization) {
        return new OrganizationVO(organization.id(), organization.parentId(),
                organization.ancestors(), organization.orgCode(), organization.orgName(),
                organization.orgType().name(), organization.level(),
                organization.leaderUserId(), organization.sortNo(),
                organization.status().name(), organization.isDeleted() ? 1 : 0,
                organization.createdAt(), organization.updatedAt());
    }
}
