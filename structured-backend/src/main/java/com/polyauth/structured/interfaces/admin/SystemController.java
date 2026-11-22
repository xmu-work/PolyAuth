package com.polyauth.structured.interfaces.admin;

import com.polyauth.structured.application.system.SystemService;
import com.polyauth.structured.application.datascope.DataScopeService;
import com.polyauth.structured.common.result.Result;
import com.polyauth.structured.interfaces.admin.dto.CreateDataScopeDTO;
import com.polyauth.structured.interfaces.admin.dto.LegacyBindRoleDataScopeDTO;
import com.polyauth.structured.interfaces.admin.vo.DataScopeVO;
import com.polyauth.structured.interfaces.admin.vo.RoleDataScopeVO;
import com.polyauth.structured.interfaces.admin.vo.SystemVO;
import com.polyauth.structured.interfaces.AuthInterceptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多系统资源控制器。只暴露 {@code /systems} 资源。
 */
@Validated
@RestController
@RequestMapping("/systems")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;
    private final DataScopeService dataScopeService;

    /**
     * 创建数据范围字典。
     */
    @PostMapping("/data-scopes")
    public Result<DataScopeVO> createDataScope(
            @Valid
            CreateDataScopeDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return Result.success(DataScopeVO.from(dataScopeService.createDataScope(
                dto.getScopeCode(), dto.getScopeName(), dto.getDescription(), dto.getSortNo(), operatorId)));
    }

    /**
     * 查询数据范围字典。
     */
    @GetMapping("/data-scopes")
    public Result<List<DataScopeVO>> listDataScopes() {
        return Result.success(dataScopeService.listDataScopes().stream()
                .map(DataScopeVO::from)
                .toList());
    }

    /**
     * 兼容旧契约的角色数据范围绑定入口。
     */
    @PostMapping("/role-data-scopes")
    public Result<RoleDataScopeVO> bindRoleDataScope(
            @Valid
            LegacyBindRoleDataScopeDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return Result.success(RoleDataScopeVO.from(dataScopeService.bindRoleDataScope(
                dto.getRoleId(), dto.getScopeType(), dto.getOrgId(), dto.getProjectId(),
                dto.getExpireTime(), operatorId)));
    }

    @PostMapping
    public Result<SystemVO> register(
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
        return Result.success(SystemVO.from(
                systemService.registerSystem(systemCode, systemName, description)));
    }

    @GetMapping
    public Result<List<SystemVO>> list() {
        return Result.success(systemService.listSystems().stream()
                .map(SystemVO::from)
                .toList());
    }

    @GetMapping("/{systemId}")
    public Result<SystemVO> get(
            @PathVariable
            @Positive
            Long systemId) {
        return Result.success(SystemVO.from(systemService.getSystem(systemId)));
    }
}
