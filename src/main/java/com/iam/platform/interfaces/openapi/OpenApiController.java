package com.iam.platform.interfaces.openapi;

import com.iam.platform.application.api.ApiService;
import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.application.decision.PermissionCheckResult;
import com.iam.platform.application.datascope.DataPermissionService;
import com.iam.platform.interfaces.openapi.dto.BatchCheckRequest;
import com.iam.platform.interfaces.openapi.dto.ResourceAuthorizationRequest;
import com.iam.platform.interfaces.openapi.vo.ResourceAuthorizationVO;
import com.iam.platform.interfaces.openapi.vo.EffectiveDataScopeVO;
import com.iam.platform.common.result.Result;
import com.iam.platform.infrastructure.persistence.entity.IamApi;
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
 * <p>后端按 API 独立鉴权：{@code /authorize} 接收 API ID，内部映射到前端权限，
 * 避免把前端按钮 ID 当作后端接口 ID。
 */
@RestController
@Validated
@RequestMapping("/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final DecisionService decisionService;
    private final ApiService apiService;
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

    @GetMapping("/authorize")
    public Result<Boolean> authorize(HttpServletRequest request,
                                     @RequestParam
                                     @Positive
                                     Long userId,
                                     @RequestParam
                                     @NotBlank
                                     @Size(max = 64)
                                     String systemCode,
                                     @RequestParam
                                     @Positive
                                     Long apiId) {
        OpenApiAuth.requireUserScope(request, userId);
        IamApi api = apiService.getApi(apiId);
        return Result.success(api.getPermissionId() != null
                && decisionService.hasPermission(userId, systemCode, api.getPermissionId()));
    }

    @PostMapping("/authorize/batch")
    public Result<PermissionCheckResult> authorizeBatch(HttpServletRequest request,
                                                        @Valid
                                                        @RequestBody
                                                        BatchCheckRequest body) {
        OpenApiAuth.requireUserScope(request, body.getUserId());
        PermissionCheckResult result = new PermissionCheckResult();
        for (Long apiId : body.getApiIds()) {
            IamApi api = apiService.getApi(apiId);
            boolean allowed = api.getPermissionId() != null
                    && decisionService.hasPermission(body.getUserId(), body.getSystemCode(), api.getPermissionId());
            result.getResults().put(apiId, allowed);
        }
        return Result.success(result);
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
