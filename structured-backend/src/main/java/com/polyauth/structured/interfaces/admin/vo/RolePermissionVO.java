package com.polyauth.structured.interfaces.admin.vo;

import com.polyauth.structured.infrastructure.persistence.entity.IamRolePermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-前端权限授权 API 响应视图。
 */
@Data
public class RolePermissionVO {

    private Long id;
    private Long roleId;
    private Long permissionId;
    private String allowedAction;
    private LocalDateTime createdAt;
    private Long createdBy;

    /**
     * 将角色权限实体转换为 VO。
     */
    public static RolePermissionVO from(IamRolePermission binding) {
        RolePermissionVO vo = new RolePermissionVO();
        vo.setId(binding.getId());
        vo.setRoleId(binding.getRoleId());
        vo.setPermissionId(binding.getPermissionId());
        vo.setAllowedAction(binding.getAllowedAction());
        vo.setCreatedAt(binding.getCreatedAt());
        vo.setCreatedBy(binding.getCreatedBy());
        return vo;
    }
}
