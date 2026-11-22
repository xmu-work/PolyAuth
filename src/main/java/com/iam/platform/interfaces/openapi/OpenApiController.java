package com.iam.platform.interfaces.openapi;

import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.application.datascope.DataPermissionService;
import com.iam.platform.interfaces.openapi.dto.ResourceAuthorizationRequest;
import com.iam.platform.interfaces.openapi.vo.ResourceAuthorizationVO;
import com.iam.platform.interfaces.openapi.vo.EffectiveDataScopeVO;
import com.iam.platform.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 开放 API 控制器（供接入子系统/网关调用）。
 *
 * <p>统一身份 API 权限由 {@code /auth/api-permissions/{operationId}} 提供；
 * 本控制器只保留数据权限资源判定和管理后台权限集合读取。
 */
@RestController
@Validated
@RequestMapping("/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final DecisionService decisionService;
    private final DataPermissionService dataPermissionService;

    /**
     * 同时校验功能权限与目标资源的数据范围。
     */
    @PostMapping("/authorize/resource")
    public Result<ResourceAuthorizationVO> authorizeResource(
            HttpServletRequest request,
            @Valid
            @RequestBody
            ResourceAuthorizationRequest body) {
        OpenApiAuth.requireUserScope(request, body.getUserId());
        return Result.success(ResourceAuthorizationVO.from(dataPermissionService.authorize(
                body.getUserId(), body.getSystemCode(), body.getApiId(), body.getTargetOrgId(),
                body.getTargetProjectId(), body.getTargetUserId())));
    }

    @GetMapping("/permissions")
    public Result<Set<Long>> permissions(HttpServletRequest request,
                                         @RequestParam
                                         @Positive
                                         Long userId,
                                         @RequestParam
                                         @NotBlank
                                         @Size(max = 64)
                                         String systemCode) {
        OpenApiAuth.requireUserScope(request, userId);
        return Result.success(decisionService.getUserPermissions(userId, systemCode));
    }

    /**
     * 查询用户当前生效的数据范围授权。
     */
    @GetMapping("/data-scopes")
    public Result<List<EffectiveDataScopeVO>> dataScopes(
            HttpServletRequest request,
            @RequestParam
            @Positive
            Long userId,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String systemCode) {
        OpenApiAuth.requireUserScope(request, userId);
        return Result.success(dataPermissionService.getEffectiveDataScopes(userId, systemCode).stream()
                .map(EffectiveDataScopeVO::from)
                .toList());
    }

}
