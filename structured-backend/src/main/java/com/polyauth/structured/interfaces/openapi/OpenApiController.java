package com.polyauth.structured.interfaces.openapi;

import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 开放 API 控制器（供接入子系统/网关调用）。
 *
 * <p>统一身份 API 权限由 {@code /auth/api-permissions/{operationId}} 提供；
 * 本控制器只提供功能权限集合读取。
 */
@RestController
@Validated
@RequestMapping("/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final DecisionService decisionService;

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

}
