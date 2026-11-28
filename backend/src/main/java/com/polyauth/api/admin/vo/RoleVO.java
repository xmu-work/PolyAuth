package com.polyauth.api.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private List<Long> parentIds;
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
        CloneFactory.copy(vo, role);
        vo.setParentIds(role.getParentId() == null
                ? List.of() : List.of(role.getParentId()));
        return vo;
    }

    /**
     * 将角色实体与多继承父角色转换为 VO。
     */
    public static RoleVO from(IamRole role, List<Long> parentIds) {
        RoleVO vo = from(role);
        vo.setParentIds(parentIds == null ? List.of() : List.copyOf(parentIds));
        return vo;
    }
}
