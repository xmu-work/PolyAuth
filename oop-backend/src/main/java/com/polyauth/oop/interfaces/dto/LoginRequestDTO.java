package com.polyauth.oop.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 登录请求 DTO。
 */
public record LoginRequestDTO(
        @NotBlank
        @Size(max = 128)
        String username,
        @NotBlank
        @Size(max = 256)
        String password,
        @NotEmpty
        List<@NotBlank String> systems) {
}
