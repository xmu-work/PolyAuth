package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.OrganizationUseCase;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.dto.UpdateOrganizationDTO;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.OrganizationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织机构管理 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationUseCase useCase;

    /**
     * 注入组织应用用例。
     */
    public OrganizationController(OrganizationUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ApiResult<OrganizationVO> create(
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String orgCode,
            @RequestParam
            @NotBlank
            @Size(max = 128)
            String orgName,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "HQ|BRANCH|DEPT|PROJECT")
            String orgType,
            @RequestParam
            @NotNull
            @Min(1)
            @Max(4)
            Integer orgLevel,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestParam(required = false)
            @Min(0)
            @Max(9999)
            Integer sortNo,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(OrganizationVO.from(useCase.create(
                orgCode, orgName, orgType, orgLevel, parentId, sortNo, operatorId)));
    }

    @GetMapping("/{orgId}")
    public ApiResult<OrganizationVO> get(
            @PathVariable
            @Positive
            Long orgId) {
        return ApiResult.success(OrganizationVO.from(useCase.get(orgId)));
    }

    @GetMapping
    public ApiResult<List<OrganizationVO>> tree(
            @RequestParam(required = false)
            @Positive
            Long rootId) {
        return ApiResult.success(useCase.tree(rootId).stream()
                .map(OrganizationVO::from).toList());
    }

    @GetMapping("/{orgId}/children")
    public ApiResult<List<OrganizationVO>> children(
            @PathVariable
            @Positive
            Long orgId) {
        return ApiResult.success(useCase.children(orgId).stream()
                .map(OrganizationVO::from).toList());
    }

    @PutMapping("/{orgId}/parent")
    public ApiResult<OrganizationVO> move(
            @PathVariable
            @Positive
            Long orgId,
            @RequestParam
            @NotNull
            @Positive
            Long newParentId,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(OrganizationVO.from(
                useCase.move(orgId, newParentId, operatorId)));
    }

    @PutMapping("/{orgId}")
    public ApiResult<OrganizationVO> update(
            @PathVariable
            @Positive
            Long orgId,
            @Valid
            @RequestBody
            UpdateOrganizationDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(OrganizationVO.from(useCase.update(
                orgId, dto.orgName(), dto.sortNo(), dto.leaderUserId(),
                dto.status(), operatorId)));
    }
}
