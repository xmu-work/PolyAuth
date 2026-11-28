package com.polyauth.api.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色更新请求。
 */
@Data
public class UpdateRoleDTO {

    @Size(max = 128)
    private String roleName;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;

    private Long parentId;

    /**
     * 多继承父角色主键集合；传入空数组表示清除继承关系。
     */
    private List<@Positive Long> parentIds;
}
