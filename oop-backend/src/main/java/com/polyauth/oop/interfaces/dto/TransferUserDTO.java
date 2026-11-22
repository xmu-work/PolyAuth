package com.polyauth.oop.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 用户调岗请求。
 *
 * @param orgId 新组织
 * @param post  新岗位
 */
public record TransferUserDTO(
        @NotNull
        @Positive
        Long orgId,
        @Size(max = 64)
        String post) {
}
