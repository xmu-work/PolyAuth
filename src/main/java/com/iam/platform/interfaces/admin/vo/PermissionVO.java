package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamPermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端菜单/按钮权限 API 响应视图。
 */
@Data
public class PermissionVO {

    private Long id;
    private Long systemId;
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String routePath;
    private String action;
    private Integer sortNo;
    private String status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将权限实体转换为 VO。
     */
    public static PermissionVO from(IamPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setSystemId(permission.getSystemId());
        vo.setParentId(permission.getParentId());
        vo.setPermissionCode(permission.getPermissionCode());
        vo.setPermissionName(permission.getPermissionName());
        vo.setPermissionType(permission.getPermissionType());
        vo.setRoutePath(permission.getRoutePath());
        vo.setAction(permission.getAction());
        vo.setSortNo(permission.getSortNo());
        vo.setStatus(permission.getStatus());
        vo.setIsDeleted(permission.getIsDeleted());
        vo.setCreatedAt(permission.getCreatedAt());
        vo.setUpdatedAt(permission.getUpdatedAt());
        return vo;
    }
}
