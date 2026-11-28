package com.polyauth.api.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.infrastructure.persistence.structured.entity.IamOrg;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织节点 API 响应视图。
 */
@Data
@CopyFrom(IamOrg.class)
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
        return CloneFactory.copy(vo, org);
    }
}
