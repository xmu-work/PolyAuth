package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.SystemManagementUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.vo.SystemVO;
import jakarta.validation.constraints.NotBlank;
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
 * 业务系统资源 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/systems")
public class SystemController {

    private final SystemManagementUseCase useCase;

    /**
     * 注入系统管理用例。
     */
    public SystemController(SystemManagementUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ApiResult<SystemVO> register(
            @RequestParam
            @NotBlank
            @Size(max = 64)
            @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}")
            String systemCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String systemName,
            @RequestParam(required = false)
            @Size(max = 500)
            String description) {
        return ApiResult.success(SystemVO.from(
                useCase.register(systemCode, systemName, description)));
    }

    @GetMapping
    public ApiResult<List<SystemVO>> list() {
        return ApiResult.success(useCase.list().stream()
                .map(SystemVO::from).toList());
    }

    @GetMapping("/{systemId}")
    public ApiResult<SystemVO> get(
            @PathVariable
            @Positive
            Long systemId) {
        return ApiResult.success(SystemVO.from(useCase.get(systemId)));
    }

}
