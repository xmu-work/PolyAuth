package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色 API 响应视图。
 */
@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String roleType;
    private Long parentId;
    private String status;
    private String description;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    /**
     * 将角色实体转换为 VO。
     */
    public static RoleVO from(IamRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setRoleType(role.getRoleType());
        vo.setParentId(role.getParentId());
        vo.setStatus(role.getStatus());
        vo.setDescription(role.getDescription());
        vo.setIsDeleted(role.getIsDeleted());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());
        vo.setCreatedBy(role.getCreatedBy());
        vo.setUpdatedBy(role.getUpdatedBy());
        return vo;
    }
}
