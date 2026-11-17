package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织节点 API 响应视图。
 */
@Data
public class OrganizationVO {

    private Long id;
    private Long parentId;
    private String ancestors;
    private String orgCode;
    private String orgName;
    private String orgType;
    private Integer orgLevel;
    private Long leaderUserId;
    private Integer sortNo;
    private String status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将组织实体转换为 VO。
     */
    public static OrganizationVO from(IamOrg org) {
        OrganizationVO vo = new OrganizationVO();
        vo.setId(org.getId());
        vo.setParentId(org.getParentId());
        vo.setAncestors(org.getAncestors());
        vo.setOrgCode(org.getOrgCode());
        vo.setOrgName(org.getOrgName());
        vo.setOrgType(org.getOrgType());
        vo.setOrgLevel(org.getOrgLevel());
        vo.setLeaderUserId(org.getLeaderUserId());
        vo.setSortNo(org.getSortNo());
        vo.setStatus(org.getStatus());
        vo.setIsDeleted(org.getIsDeleted());
        vo.setCreatedAt(org.getCreatedAt());
        vo.setUpdatedAt(org.getUpdatedAt());
        return vo;
    }
}
