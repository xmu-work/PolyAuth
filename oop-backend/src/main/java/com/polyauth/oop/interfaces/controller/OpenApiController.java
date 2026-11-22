package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.PermissionAccessUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.openapi.OpenApiAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 开放 API HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/open")
public class OpenApiController {

    private final PermissionAccessUseCase permissionAccessUseCase;

    /**
     * 注入功能权限查询用例。
     */
    public OpenApiController(PermissionAccessUseCase permissionAccessUseCase) {
        this.permissionAccessUseCase = permissionAccessUseCase;
    }

    @GetMapping("/permissions")
    public ApiResult<Set<Long>> permissions(
            HttpServletRequest request,
            @RequestParam
            @Positive
            Long userId,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String systemCode) {
        OpenApiAuth.requireUserScope(request, userId);
        return ApiResult.success(permissionAccessUseCase.permissions(
                userId, systemCode));
    }

}
