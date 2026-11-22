package com.polyauth.oop.interfaces.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 更新角色请求 DTO。
 */
public record UpdateRoleDTO(
        @Size(max = 128)
        String roleName,
        @Size(max = 500)
        String description,
        @Pattern(regexp = "ACTIVE|DISABLED")
        String status,
        @Positive
        Long parentId) {
}
