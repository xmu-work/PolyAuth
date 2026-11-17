package com.iam.platform.interfaces.admin;

import com.iam.platform.application.organization.OrganizationService;
import com.iam.platform.common.result.Result;
import com.iam.platform.interfaces.AuthInterceptor;
import com.iam.platform.interfaces.admin.dto.UpdateOrganizationDTO;
import com.iam.platform.interfaces.admin.vo.OrganizationVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
 * 分层组织部门资源控制器。
 */
@Validated
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public Result<OrganizationVO> create(
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
            Integer sortNo) {
        return Result.success(OrganizationVO.from(organizationService.createOrg(
                orgCode, orgName, orgType, orgLevel, parentId, sortNo)));
    }

    @GetMapping("/{orgId}")
    public Result<OrganizationVO> get(
            @PathVariable
            @Positive
            Long orgId) {
        return Result.success(OrganizationVO.from(organizationService.getOrg(orgId)));
    }

    @GetMapping
    public Result<List<OrganizationVO>> tree(
            @RequestParam(required = false)
            @Positive
            Long rootId) {
        return Result.success(organizationService.getOrgTree(rootId).stream()
                .map(OrganizationVO::from)
                .toList());
    }

    @GetMapping("/{orgId}/children")
    public Result<List<OrganizationVO>> children(
            @PathVariable
            @Positive
            Long orgId) {
        return Result.success(organizationService.getChildren(orgId).stream()
                .map(OrganizationVO::from)
                .toList());
    }

    @PutMapping("/{orgId}/parent")
    public Result<OrganizationVO> move(
            @PathVariable
            @Positive
            Long orgId,
            @RequestParam
            @NotNull
            @Positive
            Long newParentId) {
        return Result.success(OrganizationVO.from(
                organizationService.moveOrg(orgId, newParentId)));
    }

    /**
     * 修改组织名称、排序、负责人和启停状态。
     */
    @PutMapping("/{orgId}")
    public Result<OrganizationVO> update(
            @PathVariable
            @Positive
            Long orgId,
            @Valid
            UpdateOrganizationDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return Result.success(OrganizationVO.from(
                organizationService.updateOrg(orgId, dto.getOrgName(), dto.getSortNo(),
                        dto.getLeaderUserId(), dto.getStatus(), operatorId)));
    }
}
