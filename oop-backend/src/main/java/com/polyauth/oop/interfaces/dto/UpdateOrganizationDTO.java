package com.polyauth.oop.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 组织节点修改请求。
 *
 * @param orgName      新名称
 * @param sortNo       新排序号
 * @param leaderUserId 新负责人
 * @param status       新状态
 */
public record UpdateOrganizationDTO(
        @Size(max = 128)
        String orgName,
        @Min(0)
        Integer sortNo,
        Long leaderUserId,
        @Pattern(regexp = "ACTIVE|DISABLED")
        String status) {
}
