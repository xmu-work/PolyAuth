package com.polyauth.api.admin;

import com.polyauth.service.port.auth.AuthUseCase;
import com.polyauth.api.shared.Result;
import com.polyauth.api.shared.AuthInterceptor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器（管理端）。
 *
 * <p>对应 API：/api/v1/auth/*
 */
@RestController
@Validated
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authService;

    /**
     * 自助修改密码。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String oldPassword,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String newPassword) {
        authService.changePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    /**
     * 令牌校验。
     */
    @GetMapping("/validate")
    public Result<Boolean> validate(
            @RequestHeader("Authorization")
            String authorization) {
        return Result.success(authService.validateToken(AuthInterceptor.bearerToken(authorization)));
    }
}
