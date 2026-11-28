package com.polyauth.api.admin;

import com.polyauth.service.port.role.RoleUseCase;
import com.polyauth.api.shared.Result;
import com.polyauth.api.admin.dto.UpdateRoleDTO;
import com.polyauth.api.admin.vo.RoleVO;
import com.polyauth.api.admin.vo.RolePermissionVO;
import com.polyauth.api.admin.vo.UserRoleVO;
import com.polyauth.api.shared.AuthInterceptor;
import com.polyauth.api.shared.validation.PositiveIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 角色资源控制器。
 *
 * <p>所有 URL 都以 {@code /roles} 为根；角色生命周期、用户角色分配和角色权限分配
 * 都能从 URL 最终资源直接定位到本控制器。
 */
@Validated
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleUseCase roleService;

    @PostMapping
    public Result<RoleVO> create(
            @RequestParam(required = false)
            @Size(max = 64)
            String roleCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String roleName,
            @RequestParam(defaultValue = "FUNC")
            @NotBlank
            @Pattern(regexp = "FUNC")
            String roleType,
            @RequestParam(required = false)
            @Size(max = 500)
            String description,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestParam(required = false)
            List<@Positive Long> parentIds) {
        List<Long> effectiveParents = parentIds != null
                ? parentIds
                : (parentId == null ? List.of() : List.of(parentId));
        var role = roleService.createRole(roleCode, roleName, roleType, description, effectiveParents);
        return Result.success(RoleVO.from(role, roleService.getParentIds(role.getId())));
    }

    /**
     * 修改角色名称、描述、状态和父角色。
     */
    @PutMapping("/{roleId}")
    public Result<RoleVO> update(
            @PathVariable
            @Positive
            Long roleId,
            @Valid
            @RequestBody
            UpdateRoleDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        List<Long> effectiveParents = dto.getParentIds() != null
                ? dto.getParentIds()
                : (dto.getParentId() == null ? List.of() : List.of(dto.getParentId()));
        var role = roleService.updateRole(
                roleId, dto.getRoleName(), dto.getDescription(),
                dto.getStatus(), effectiveParents, operatorId);
        return Result.success(RoleVO.from(role, roleService.getParentIds(roleId)));
    }

    /**
     * 查询父角色和子角色关系。
     */
    @GetMapping("/{roleId}/exclusions")
    public Result<List<Long>> exclusions(
            @PathVariable
            @Positive
            Long roleId) {
        return Result.success(roleService.listRoleExclusions(roleId));
    }

    /**
     * 新建角色互斥关系。
     */
    @PostMapping("/{roleId}/exclusions")
    public Result<Void> bindExclusion(
            @PathVariable
            @Positive
            Long roleId,
            @RequestParam
            @NotNull
            @Positive
            Long excludedRoleId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleService.bindRoleExclusion(roleId, excludedRoleId, operatorId);
        return Result.success();
    }

    /**
     * 删除角色互斥关系。
     */
    @DeleteMapping("/{roleId}/exclusions/{excludedRoleId}")
    public Result<Void> unbindExclusion(
            @PathVariable
            @Positive
            Long roleId,
            @PathVariable
            @Positive
            Long excludedRoleId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleService.unbindRoleExclusion(roleId, excludedRoleId, operatorId);
        return Result.success();
    }

    @GetMapping("/{roleId}")
    public Result<RoleVO> get(
            @PathVariable
            @Positive
            Long roleId) {
        var role = roleService.getRole(roleId);
        return Result.success(RoleVO.from(role, roleService.getParentIds(roleId)));
    }

    @GetMapping
    public Result<List<RoleVO>> list() {
        var roles = roleService.listRoles();
        Map<Long, List<Long>> parentIds = roleService.getParentIds(
                roles.stream().map(role -> role.getId()).toList());
        return Result.success(roles.stream()
                .map(role -> RoleVO.from(role, parentIds.getOrDefault(role.getId(), List.of())))
                .toList());
    }

    /**
     * 将角色分配给用户。
     */
    @PostMapping("/{roleId}/assignments")
    public Result<UserRoleVO> assignUser(
            @PathVariable
            @Positive
            Long roleId,
            @RequestParam
            @NotNull
            @Positive
            Long userId,
            @RequestParam(required = false)
            LocalDateTime expireTime,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long grantBy,
            @RequestParam(required = false)
            @Size(max = 255)
            String grantReason) {
        return Result.success(UserRoleVO.from(
                roleService.grantRole(userId, roleId, expireTime, grantBy, grantReason)));
    }

    @DeleteMapping("/{roleId}/assignments/{userId}")
    public Result<Void> revokeUser(
            @PathVariable
            @Positive
            Long roleId,
            @PathVariable
            @Positive
            Long userId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleService.revokeRole(userId, roleId, operatorId);
        return Result.success();
    }

    @GetMapping("/{roleId}/assignments")
    public Result<List<UserRoleVO>> roleAssignments(
            @PathVariable
            @Positive
            Long roleId) {
        return Result.success(roleService.getRoleUsers(roleId).stream()
                .map(UserRoleVO::from)
                .toList());
    }

    @GetMapping("/assignments")
    public Result<List<UserRoleVO>> userAssignments(
            @RequestParam
            @NotNull
            @Positive
            Long userId) {
        return Result.success(roleService.getUserRoles(userId).stream()
                .map(UserRoleVO::from)
                .toList());
    }

    /**
     * 给角色授予前端菜单/按钮权限。
     */
    @PostMapping("/{roleId}/permissions")
    public Result<Void> bindPermissions(
            @PathVariable
            @Positive
            Long roleId,
            @RequestBody
            @NotNull
            @Size(max = 200)
            @PositiveIds
            List<Long> permissionIds,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "NONE|VIEW|ALL|VIEW_EDIT|VIEW_ADD|VIEW_APPROVE|VIEW_EXPORT|"
                    + "VIEW_EDIT_APPROVE|VIEW_ADD_EDIT|VIEW_EDIT_EXPORT|VIEW_SELF|VIEW_EDIT_SELF")
            String allowedAction,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long createdBy) {
        roleService.bindRolePermissions(roleId, permissionIds, allowedAction, createdBy);
        return Result.success();
    }

    @DeleteMapping("/{roleId}/permissions")
    public Result<Void> unbindPermissions(
            @PathVariable
            @Positive
            Long roleId,
            @RequestBody
            @NotNull
            @Size(max = 200)
            @PositiveIds
            List<Long> permissionIds,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        roleService.unbindRolePermissions(roleId, permissionIds, operatorId);
        return Result.success();
    }

    @GetMapping("/{roleId}/permissions")
    public Result<List<RolePermissionVO>> rolePermissions(
            @PathVariable
            @Positive
            Long roleId) {
        return Result.success(roleService.getRolePermissions(roleId).stream()
                .map(RolePermissionVO::from)
                .toList());
    }
}
