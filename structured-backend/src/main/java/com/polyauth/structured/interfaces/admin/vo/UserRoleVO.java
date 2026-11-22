package com.polyauth.structured.interfaces.admin.vo;

import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色授权 API 响应视图。
 */
@Data
public class UserRoleVO {

    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private Long grantBy;
    private String grantReason;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将用户角色授权实体转换为 VO。
     */
    public static UserRoleVO from(IamUserRole grant) {
        UserRoleVO vo = new UserRoleVO();
        vo.setId(grant.getId());
        vo.setUserId(grant.getUserId());
        vo.setRoleId(grant.getRoleId());
        vo.setEffectiveTime(grant.getEffectiveTime());
        vo.setExpireTime(grant.getExpireTime());
        vo.setGrantBy(grant.getGrantBy());
        vo.setGrantReason(grant.getGrantReason());
        vo.setStatus(grant.getStatus());
        vo.setCreatedAt(grant.getCreatedAt());
        vo.setUpdatedAt(grant.getUpdatedAt());
        return vo;
    }
}
