package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.result.IdentityDepartment;
import com.polyauth.oop.application.result.IdentityDepartmentUser;
import com.polyauth.oop.application.usecase.IdentityAccessUseCase;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.IdentityVOs;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一身份完整查询契约 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/auth")
public class IdentityAuthController {

    private final IdentityAccessUseCase useCase;

    /**
     * 注入统一身份查询用例。
     */
    public IdentityAuthController(IdentityAccessUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/me")
    public IdentityVOs.CurrentUser currentUser(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode) {
        return IdentityVOs.CurrentUser.from(
                useCase.currentUser(userId, systemCode));
    }

    @GetMapping("/permissions/{permission}")
    public IdentityVOs.PermissionCheck checkPermission(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            String permission) {
        return new IdentityVOs.PermissionCheck(
                useCase.hasPermissionCode(userId, systemCode, permission));
    }

    @GetMapping("/departments")
    public IdentityVOs.DepartmentPage departments(
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
        IdentityAccessUseCase.PageSlice<IdentityDepartment>
                result = useCase.departments(keyword, parentId, page, pageSize);
        return new IdentityVOs.DepartmentPage(page, pageSize, result.total(),
                result.items().stream().map(IdentityVOs.Department::from).toList());
    }

    @GetMapping("/departments/{departmentId}/users")
    public IdentityVOs.DepartmentUserPage departmentUsers(
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
        IdentityAccessUseCase.PageSlice<IdentityDepartmentUser>
                result = useCase.departmentUsers(departmentId, keyword, page, pageSize);
        return new IdentityVOs.DepartmentUserPage(page, pageSize, result.total(),
                result.items().stream().map(IdentityVOs.DepartmentUser::from).toList());
    }

    @GetMapping("/users/{userId}")
    public IdentityVOs.User user(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @PathVariable
            @Size(min = 1, max = 64)
            String userId) {
        return IdentityVOs.User.from(
                useCase.lookupUser(operatorId, systemCode, userId));
    }
}
