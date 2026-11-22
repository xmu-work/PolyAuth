package com.polyauth.structured.interfaces.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.structured.infrastructure.persistence.entity.IamRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色 API 响应视图。
 */
@Data
@CopyFrom(IamRole.class)
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
        return CloneFactory.copy(vo, role);
    }
}
