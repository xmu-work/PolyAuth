package com.polyauth.structured.application.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 登录请求 DTO。
 */
@Data
public class LoginRequest {

    @NotBlank
    @Size(max = 128)
    private String username;

    @NotBlank
    @Size(max = 256)
    private String password;

    @NotEmpty
    private List<@NotBlank String> systems;

    private String clientIp;
}
