package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.command.LoginCommand;
import com.polyauth.oop.application.result.LoginResult;
import com.polyauth.oop.application.usecase.AuthenticationUseCase;
import com.polyauth.oop.application.usecase.OperationAccessUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.dto.LoginRequestDTO;
import com.polyauth.oop.interfaces.filter.RequestIdFilter;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.ApiPermissionCheckVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * OOP 后端本地认证与统一身份基础接口。
 */
@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationUseCase authenticationUseCase;
    private final OperationAccessUseCase operationAccessUseCase;

    /**
     * 注入认证与 API 权限用例。
     */
    public AuthController(AuthenticationUseCase authenticationUseCase,
                          OperationAccessUseCase operationAccessUseCase) {
        this.authenticationUseCase = authenticationUseCase;
        this.operationAccessUseCase = operationAccessUseCase;
    }

    /**
     * 账号密码登录并签发 JWT。
     */
    @PostMapping("/login")
    public LoginResult login(
            @Valid
            @RequestBody
            LoginRequestDTO request,
            HttpServletRequest servletRequest) {
        return authenticationUseCase.login(new LoginCommand(
                request.username(), request.password(), request.systems(),
                servletRequest.getRemoteAddr(), RequestIdFilter.requestId(servletRequest)));
    }

    /**
     * 吊销当前访问令牌。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader("Authorization")
            String authorization) {
        authenticationUseCase.logout(AuthInterceptor.bearerToken(authorization));
    }

    /**
     * 校验当前访问令牌是否有效。
     */
    @GetMapping("/validate")
    public ApiResult<Boolean> validate(
            @RequestHeader("Authorization")
            String authorization) {
        return ApiResult.success(
                authenticationUseCase.validate(AuthInterceptor.bearerToken(authorization)));
    }

    /**
     * 按 operationId 判断当前用户是否具有 API 调用权限。
     */
    @GetMapping("/api-permissions/{operationId}")
    public ApiPermissionCheckVO apiPermission(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            @Size(min = 1, max = 128)
            String operationId) {
        return new ApiPermissionCheckVO(operationId,
                operationAccessUseCase.operationAllowed(userId, systemCode, operationId));
    }

    /**
     * 当前用户自助修改密码。
     */
    @PutMapping("/password")
    public ApiResult<Void> changePassword(
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
        authenticationUseCase.changePassword(userId, oldPassword, newPassword);
        return ApiResult.success();
    }
}
