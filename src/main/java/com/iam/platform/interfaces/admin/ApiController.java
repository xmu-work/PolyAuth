package com.iam.platform.interfaces.admin;

import com.iam.platform.application.api.ApiService;
import com.iam.platform.common.result.Result;
import com.iam.platform.interfaces.admin.vo.ApiVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后端 API 资源控制器。
 */
@Validated
@RestController
@RequestMapping("/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @PostMapping
    public Result<ApiVO> register(
            @RequestParam
            @NotNull
            @Positive
            Long systemId,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String apiCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String apiName,
            @RequestParam
            @NotBlank
            @Size(max = 255)
            String apiPath,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH")
            String httpMethod) {
        return Result.success(ApiVO.from(
                apiService.registerApi(systemId, apiCode, apiName, apiPath, httpMethod)));
    }

    @GetMapping
    public Result<List<ApiVO>> list(
            @RequestParam
            @NotNull
            @Positive
            Long systemId) {
        return Result.success(apiService.listApis(systemId).stream()
                .map(ApiVO::from)
                .toList());
    }

    @GetMapping("/{apiId}")
    public Result<ApiVO> get(
            @PathVariable
            @Positive
            Long apiId) {
        return Result.success(ApiVO.from(apiService.getApi(apiId)));
    }
}
