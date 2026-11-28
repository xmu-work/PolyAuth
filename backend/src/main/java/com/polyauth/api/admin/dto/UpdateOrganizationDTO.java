package com.polyauth.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 组织节点更新请求。
 */
@Data
public class UpdateOrganizationDTO {

    @Size(max = 128)
    private String orgName;

    @Min(0)
    private Integer sortNo;

    private Long leaderUserId;

    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;
}
