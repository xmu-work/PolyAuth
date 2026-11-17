package com.iam.platform.interfaces.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
}
