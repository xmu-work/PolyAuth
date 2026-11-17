package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamRoleDataScope;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色数据范围授权响应视图。
 */
@Data
public class RoleDataScopeVO {

    private Long id;
    private Long roleId;
    private String scopeType;
    private Long orgId;
    private Long projectId;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;

    /**
     * 将角色数据范围实体转换为对外视图。
     */
    public static RoleDataScopeVO from(IamRoleDataScope binding) {
        RoleDataScopeVO vo = new RoleDataScopeVO();
        vo.setId(binding.getId());
        vo.setRoleId(binding.getRoleId());
        vo.setScopeType(binding.getScopeType());
        vo.setOrgId(binding.getOrgId());
        vo.setProjectId(binding.getProjectId());
        vo.setEffectiveTime(binding.getEffectiveTime());
        vo.setExpireTime(binding.getExpireTime());
        vo.setStatus(binding.getStatus());
        vo.setCreatedBy(binding.getCreatedBy());
        vo.setCreatedAt(binding.getCreatedAt());
        return vo;
    }
}
