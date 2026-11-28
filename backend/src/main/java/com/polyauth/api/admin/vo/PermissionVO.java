package com.polyauth.api.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端菜单/按钮权限 API 响应视图。
 */
@Data
@CopyFrom(IamPermission.class)
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
        return CloneFactory.copy(vo, permission);
    }
}
