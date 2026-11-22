package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.ApiCatalogUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.vo.ApiVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后端 API 目录 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/apis")
public class ApiCatalogController {

    private final ApiCatalogUseCase useCase;

    /**
     * 注入 API 目录用例。
     */
    public ApiCatalogController(ApiCatalogUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ApiResult<ApiVO> register(
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
        return ApiResult.success(ApiVO.from(useCase.register(
                systemId, apiCode, apiName, apiPath, httpMethod)));
    }

    @GetMapping
    public ApiResult<List<ApiVO>> list(
            @RequestParam
            @NotNull
            @Positive
            Long systemId) {
        return ApiResult.success(useCase.list(systemId).stream()
                .map(ApiVO::from).toList());
    }

    @GetMapping("/{apiId}")
    public ApiResult<ApiVO> get(
            @PathVariable
            @Positive
            Long apiId) {
        return ApiResult.success(ApiVO.from(useCase.get(apiId)));
    }
}
