package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.result.IdentityAuditLog;
import com.polyauth.oop.application.result.IdentityDepartment;
import com.polyauth.oop.application.result.IdentityDepartmentUser;
import com.polyauth.oop.application.usecase.IdentityAccessUseCase;
import com.polyauth.oop.interfaces.dto.IdentityAuditWriteDTO;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.IdentityVOs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 统一身份完整查询契约 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/auth")
public class IdentityAuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthController.class);
    private static final int MAX_LOG_VALUE_LENGTH = 128;

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
        boolean allowed = useCase.hasPermissionCode(userId, systemCode, permission);
        LOGGER.info("permission-check system={} userId={} permission={} allowed={}",
                sanitizeLogValue(systemCode), userId, sanitizeLogValue(permission), allowed);
        return new IdentityVOs.PermissionCheck(allowed);
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

    /**
     * 按操作人、操作对象与时间窗口分页查询外部业务统一身份审计日志。
     */
    @GetMapping("/audit-logs")
    public IdentityVOs.AuditLogPage auditLogs(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @RequestParam(required = false)
            @Size(max = 128)
            String actor,
            @RequestParam(required = false)
            @Positive
            Long actorId,
            @RequestParam(required = false)
            @Size(max = 500)
            String target,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int pageSize) {
        IdentityAccessUseCase.PageSlice<IdentityAuditLog> result = useCase.queryAuditLogs(
                operatorId, systemCode, actor, actorId, target, from, to, page, pageSize);
        return IdentityVOs.AuditLogPage.from(page, pageSize, result);
    }

    /**
     * 按幂等键写入外部业务统一身份审计日志。
     */
    @PostMapping("/audit-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityVOs.AuditLog writeAuditLog(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_SYSTEM_CODE)
            String systemCode,
            @Valid
            @RequestBody
            IdentityAuditWriteDTO request,
            HttpServletRequest servletRequest) {
        return IdentityVOs.AuditLog.from(useCase.writeAuditLog(
                userId,
                systemCode,
                request.requestId(),
                request.type(),
                request.objectType(),
                request.objectId(),
                request.target(),
                request.before(),
                request.after(),
                servletRequest.getRemoteAddr()));
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_').replace('\t', ' ').trim();
        if (sanitized.isEmpty()) {
            return "-";
        }
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }
}
