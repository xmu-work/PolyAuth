package com.polyauth.structured.interfaces.identity;

import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.auth.LoginRequest;
import com.polyauth.structured.application.auth.LoginResponse;
import com.polyauth.structured.application.identity.IdentityService;
import com.polyauth.structured.interfaces.AuthInterceptor;
import com.polyauth.structured.interfaces.identity.vo.ApiAuthorityCheckVO;
import com.polyauth.structured.interfaces.identity.vo.IdentityCurrentUserVO;
import com.polyauth.structured.interfaces.identity.vo.IdentityDepartmentPageVO;
import com.polyauth.structured.interfaces.identity.vo.IdentityDepartmentUserPageVO;
import com.polyauth.structured.interfaces.identity.vo.IdentityUserVO;
import com.polyauth.structured.interfaces.identity.vo.PermissionCheckVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向外部业务系统的统一身份鉴权契约控制器。
 */
@RestController
@RequestMapping("/auth")
@Validated
@RequiredArgsConstructor
public class IdentityAuthController {

    private final IdentityService identityService;
    private final AuthService authService;

    /**
     * 账号密码登录并签发带目标系统 audience 的 JWT。
     */
    @PostMapping("/login")
    public LoginResponse login(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest) {
        request.setClientIp(servletRequest.getRemoteAddr());
        return authService.login(request);
    }

    /**
     * 吊销当前访问令牌。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(AuthInterceptor.bearerToken(authorization));
    }

    /**
     * 查询当前用户、角色和目标系统前端权限码。
     */
    @GetMapping("/me")
    public IdentityCurrentUserVO currentUser(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode) {
        return IdentityCurrentUserVO.from(identityService.getCurrentUser(userId, systemCode));
    }

    /**
     * 校验当前用户是否具有指定前端权限码。
     */
    @GetMapping("/permissions/{permission}")
    public PermissionCheckVO checkPermission(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            String permission) {
        return new PermissionCheckVO(
                identityService.hasPermissionCode(userId, systemCode, permission));
    }

    /**
     * 按层级分页查询统一身份部门。
     */
    @GetMapping("/departments")
    public IdentityDepartmentPageVO departments(
            @RequestParam(required = false)
            @Size(max = 100)
            String keyword,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int pageSize) {
        return IdentityDepartmentPageVO.from(page, pageSize,
                identityService.pageDepartments(keyword, parentId, page, pageSize));
    }

    /**
     * 判断当前用户能否调用指定 operationId 对应的后端 API。
     */
    @GetMapping("/api-permissions/{operationId}")
    public ApiAuthorityCheckVO apiPermission(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            @Size(min = 1, max = 128)
            String operationId) {
        return new ApiAuthorityCheckVO(operationId,
                identityService.hasApiOperation(userId, systemCode, operationId));
    }

    /**
     * 分页查询指定部门下的统一身份用户。
     */
    @GetMapping("/departments/{departmentId}/users")
    public IdentityDepartmentUserPageVO departmentUsers(
            @PathVariable
            @Positive
            Long departmentId,
            @RequestParam(required = false)
            @Size(max = 100)
            String keyword,
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int pageSize) {
        return IdentityDepartmentUserPageVO.from(page, pageSize,
                identityService.pageDepartmentUsers(departmentId, keyword, page, pageSize));
    }

    /**
     * 由管理员按统一身份用户标识查询姓名与工号。
     */
    @GetMapping("/users/{userId}")
    public IdentityUserVO user(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            @Size(min = 1, max = 64)
            String userId) {
        return IdentityUserVO.from(identityService.lookupUser(operatorId, systemCode, userId));
    }
}
