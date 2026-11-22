package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.PermissionCatalogUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.vo.ApiVO;
import com.polyauth.oop.interfaces.vo.PermissionVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前端权限树 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionCatalogUseCase useCase;

    /**
     * 注入权限目录用例。
     */
    public PermissionController(PermissionCatalogUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ApiResult<PermissionVO> create(
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
            @Pattern(regexp = "NONE|VIEW|ALL|VIEW_EDIT|VIEW_ADD|VIEW_APPROVE|"
                    + "VIEW_EXPORT|VIEW_EDIT_APPROVE|VIEW_ADD_EDIT|VIEW_EDIT_EXPORT|"
                    + "VIEW_SELF|VIEW_EDIT_SELF")
            String action,
            @RequestParam(required = false)
            @Min(0)
            @Max(9999)
            Integer sortNo) {
        return ApiResult.success(PermissionVO.from(useCase.create(
                systemId, parentId, permissionCode, permissionName,
                permissionType, routePath, action, sortNo)));
    }

    @GetMapping
    public ApiResult<List<PermissionVO>> list(
            @RequestParam
            @NotNull
            @Positive
            Long systemId) {
        return ApiResult.success(useCase.list(systemId).stream()
                .map(PermissionVO::from).toList());
    }

    @PostMapping("/{permissionId}/apis/{apiId}")
    public ApiResult<ApiVO> bindApi(
            @PathVariable
            @Positive
            Long permissionId,
            @PathVariable
            @Positive
            Long apiId) {
        return ApiResult.success(ApiVO.from(useCase.bindApi(permissionId, apiId)));
    }

    @DeleteMapping("/{permissionId}/apis/{apiId}")
    public ApiResult<Void> unbindApi(
            @PathVariable
            @Positive
            Long permissionId,
            @PathVariable
            @Positive
            Long apiId) {
        useCase.unbindApi(permissionId, apiId);
        return ApiResult.success();
    }

    @GetMapping("/{permissionId}/apis")
    public ApiResult<List<ApiVO>> permissionApis(
            @PathVariable
            @Positive
            Long permissionId) {
        return ApiResult.success(useCase.permissionApis(permissionId).stream()
                .map(ApiVO::from).toList());
    }
}
