package com.polyauth.api.admin;

import com.polyauth.service.port.permission.PermissionUseCase;
import com.polyauth.api.shared.Result;
import com.polyauth.api.admin.vo.ApiVO;
import com.polyauth.api.admin.vo.PermissionVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前端菜单/按钮权限树控制器。
 */
@Validated
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionUseCase permissionService;

    @PostMapping
    public Result<PermissionVO> create(
            @RequestParam
            @NotNull
            @Positive
            Long systemId,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String permissionCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String permissionName,
            @RequestParam(defaultValue = "MENU")
            @NotBlank
            @Pattern(regexp = "MENU|BUTTON")
            String permissionType,
            @RequestParam(required = false)
            @Size(max = 255)
            String routePath,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "NONE|VIEW|ALL|VIEW_EDIT|VIEW_ADD|VIEW_APPROVE|VIEW_EXPORT|"
                    + "VIEW_EDIT_APPROVE|VIEW_ADD_EDIT|VIEW_EDIT_EXPORT|VIEW_SELF|VIEW_EDIT_SELF")
            String action,
            @RequestParam(required = false)
            @Min(0)
            @Max(9999)
            Integer sortNo) {
        return Result.success(PermissionVO.from(permissionService.createPermission(
                systemId, parentId, permissionCode, permissionName,
                permissionType, routePath, action, sortNo)));
    }

    @GetMapping
    public Result<List<PermissionVO>> list(
            @RequestParam
            @NotNull
            @Positive
            Long systemId) {
        return Result.success(permissionService.listPermissions(systemId).stream()
                .map(PermissionVO::from)
                .toList());
    }

    @PostMapping("/{permissionId}/apis/{apiId}")
    public Result<ApiVO> bindApi(
            @PathVariable
            @Positive
            Long permissionId,
            @PathVariable
            @Positive
            Long apiId) {
        return Result.success(ApiVO.from(permissionService.bindApi(permissionId, apiId)));
    }

    @DeleteMapping("/{permissionId}/apis/{apiId}")
    public Result<Void> unbindApi(
            @PathVariable
            @Positive
            Long permissionId,
            @PathVariable
            @Positive
            Long apiId) {
        permissionService.unbindApi(permissionId, apiId);
        return Result.success();
    }

    @GetMapping("/{permissionId}/apis")
    public Result<List<ApiVO>> permissionApis(
            @PathVariable
            @Positive
            Long permissionId) {
        return Result.success(permissionService.getPermissionApis(permissionId).stream()
                .map(ApiVO::from)
                .toList());
    }
}
