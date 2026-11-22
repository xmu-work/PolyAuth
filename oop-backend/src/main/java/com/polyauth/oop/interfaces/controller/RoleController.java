package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.command.CreateRoleCommand;
import com.polyauth.oop.application.command.GrantRoleCommand;
import com.polyauth.oop.application.command.UpdateRoleCommand;
import com.polyauth.oop.application.usecase.RoleManagementUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.dto.UpdateRoleDTO;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.RolePermissionVO;
import com.polyauth.oop.interfaces.vo.RoleVO;
import com.polyauth.oop.interfaces.vo.UserRoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * OOP 后端角色管理接口，保持与结构化后端相同路径。
 */
@RestController
@Validated
@RequestMapping("/roles")
public class RoleController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RoleManagementUseCase roleManagementUseCase;

    /**
     * 注入角色管理应用用例。
     */
    public RoleController(RoleManagementUseCase roleManagementUseCase) {
        this.roleManagementUseCase = roleManagementUseCase;
    }

    /**
     * 创建角色。
     */
    @PostMapping
    public ApiResult<RoleVO> create(
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String roleCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String roleName,
            @RequestParam(defaultValue = "FUNC")
            @Pattern(regexp = "FUNC")
            String roleType,
            @RequestParam(required = false)
            @Size(max = 500)
            String description,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(RoleVO.from(roleManagementUseCase.createRole(
                new CreateRoleCommand(roleCode, roleName, roleType,
                        description, parentId, operatorId))));
    }

    /**
     * 修改角色。
     */
    @PutMapping("/{roleId}")
    public ApiResult<RoleVO> update(
            @PathVariable
            @Positive
            Long roleId,
            @Valid
            @RequestBody
            UpdateRoleDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(RoleVO.from(roleManagementUseCase.updateRole(
                new UpdateRoleCommand(roleId, dto.roleName(), dto.description(),
                        dto.status(), dto.parentId(), operatorId))));
    }

    /**
     * 查询角色详情。
     */
    @GetMapping("/{roleId}")
    public ApiResult<RoleVO> get(
            @PathVariable
            @Positive
            Long roleId) {
        return ApiResult.success(RoleVO.from(roleManagementUseCase.getRole(roleId)));
    }

    /**
     * 查询全部角色。
     */
    @GetMapping
    public ApiResult<List<RoleVO>> list() {
        return ApiResult.success(roleManagementUseCase.listRoles().stream()
                .map(RoleVO::from)
                .toList());
    }

    /**
     * 向用户授予角色。
     */
    @PostMapping("/{roleId}/assignments")
    public ApiResult<UserRoleVO> assignUser(
            @PathVariable
            @Positive
            Long roleId,
            @RequestParam
            @NotNull
            @Positive
            Long userId,
            @RequestParam(required = false)
            LocalDateTime expireTime,
            @RequestParam(required = false)
            @Size(max = 255)
            String grantReason,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long grantBy) {
        return ApiResult.success(UserRoleVO.from(roleManagementUseCase.grantRole(
                new GrantRoleCommand(userId, roleId, instant(expireTime),
                        grantBy, grantReason))));
    }

    /**
     * 撤销用户角色。
     */
    @DeleteMapping("/{roleId}/assignments/{userId}")
    public ApiResult<Void> revokeUser(
            @PathVariable
            @Positive
            Long roleId,
            @PathVariable
            @Positive
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleManagementUseCase.revokeRole(userId, roleId, operatorId);
        return ApiResult.success();
    }

    /**
     * 按用户查询角色授权。
     */
    @GetMapping("/assignments")
    public ApiResult<List<UserRoleVO>> userAssignments(
            @RequestParam
            @NotNull
            @Positive
            Long userId) {
        return ApiResult.success(roleManagementUseCase.userAssignments(userId).stream()
                .map(UserRoleVO::from)
                .toList());
    }

    /**
     * 查询角色下的用户分配。
     */
    @GetMapping("/{roleId}/assignments")
    public ApiResult<List<UserRoleVO>> roleAssignments(
            @PathVariable
            @Positive
            Long roleId) {
        return ApiResult.success(roleManagementUseCase.roleAssignments(roleId).stream()
                .map(UserRoleVO::from).toList());
    }

    /**
     * 给角色绑定前端权限。
     */
    @PostMapping("/{roleId}/permissions")
    public ApiResult<Void> bindPermissions(
            @PathVariable
            @Positive
            Long roleId,
            @RequestBody
            @NotNull
            @Size(max = 200)
            List<@Positive Long> permissionIds,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "NONE|VIEW|ALL|VIEW_EDIT|VIEW_ADD|VIEW_APPROVE|"
                    + "VIEW_EXPORT|VIEW_EDIT_APPROVE|VIEW_ADD_EDIT|VIEW_EDIT_EXPORT|"
                    + "VIEW_SELF|VIEW_EDIT_SELF")
            String allowedAction,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleManagementUseCase.bindPermissions(
                roleId, permissionIds, allowedAction, operatorId);
        return ApiResult.success();
    }

    /**
     * 查询角色已经绑定的前端权限。
     */
    @GetMapping("/{roleId}/permissions")
    public ApiResult<List<RolePermissionVO>> rolePermissions(
            @PathVariable
            @Positive
            Long roleId) {
        return ApiResult.success(roleManagementUseCase.rolePermissions(roleId).stream()
                .map(RolePermissionVO::from)
                .toList());
    }

    /**
     * 解除角色前端权限绑定。
     */
    @DeleteMapping("/{roleId}/permissions")
    public ApiResult<Void> unbindPermissions(
            @PathVariable
            @Positive
            Long roleId,
            @RequestBody
            @NotNull
            @Size(max = 200)
            List<@Positive Long> permissionIds,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleManagementUseCase.unbindPermissions(
                roleId, permissionIds, operatorId);
        return ApiResult.success();
    }

    /**
     * 查询角色互斥关系。
     */
    @GetMapping("/{roleId}/exclusions")
    public ApiResult<List<Long>> exclusions(
            @PathVariable
            @Positive
            Long roleId) {
        return ApiResult.success(roleManagementUseCase.exclusions(roleId));
    }

    /**
     * 新建角色互斥关系。
     */
    @PostMapping("/{roleId}/exclusions")
    public ApiResult<Void> bindExclusion(
            @PathVariable
            @Positive
            Long roleId,
            @RequestParam
            @NotNull
            @Positive
            Long excludedRoleId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleManagementUseCase.bindExclusion(
                roleId, excludedRoleId, operatorId);
        return ApiResult.success();
    }

    /**
     * 删除角色互斥关系。
     */
    @DeleteMapping("/{roleId}/exclusions/{excludedRoleId}")
    public ApiResult<Void> unbindExclusion(
            @PathVariable
            @Positive
            Long roleId,
            @PathVariable
            @Positive
            Long excludedRoleId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleManagementUseCase.unbindExclusion(
                roleId, excludedRoleId, operatorId);
        return ApiResult.success();
    }


    private static java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }
}
