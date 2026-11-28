package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.domain.model.ApiEndpoint;
import com.polyauth.service.ddd.domain.model.OperationAction;
import com.polyauth.service.ddd.domain.model.PermissionNode;
import com.polyauth.service.ddd.domain.repository.ApiAdministrationRepository;
import com.polyauth.service.ddd.domain.repository.PermissionAdministrationRepository;
import com.polyauth.service.ddd.domain.repository.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前端权限树管理和 API 映射用例。
 */
@Service
@Transactional
public class PermissionCatalogUseCase {

    private final PermissionAdministrationRepository permissionRepository;
    private final ApiAdministrationRepository apiRepository;
    private final SystemRepository systemRepository;

    /**
     * 注入权限、API 与系统仓储。
     */
    public PermissionCatalogUseCase(
            PermissionAdministrationRepository permissionRepository,
            ApiAdministrationRepository apiRepository,
            SystemRepository systemRepository) {
        this.permissionRepository = permissionRepository;
        this.apiRepository = apiRepository;
        this.systemRepository = systemRepository;
    }

    /**
     * 创建菜单或按钮权限节点。
     */
    public PermissionNode create(Long systemId, Long parentId, String code,
                                 String name, String type, String routePath,
                                 String action, Integer sortNo) {
        systemRepository.findById(systemId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "系统不存在: " + systemId));
        if (parentId != null) {
            PermissionNode parent = requirePermission(parentId);
            if (!systemId.equals(parent.systemId())) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID,
                        "父子权限必须属于同一系统");
            }
        }
        if (permissionRepository.findBySystemAndCode(systemId, code).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE,
                    "权限编码已存在: " + code);
        }
        OperationAction operation;
        try {
            operation = OperationAction.valueOf(action);
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "未知权限操作: " + action);
        }
        return permissionRepository.save(PermissionNode.create(
                systemId, parentId, code, name, type, routePath,
                operation, sortNo, LocalDateTime.now()));
    }

    /**
     * 查询系统权限节点。
     */
    @Transactional(readOnly = true)
    public List<PermissionNode> list(Long systemId) {
        return permissionRepository.findBySystem(systemId);
    }

    /**
     * 将 API 绑定到前端权限。
     */
    public ApiEndpoint bindApi(Long permissionId, Long apiId) {
        PermissionNode permission = requirePermission(permissionId);
        ApiEndpoint api = requireApi(apiId);
        if (!permission.systemId().equals(api.systemId())) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "权限与 API 必须属于同一系统");
        }
        return apiRepository.save(
                api.mapPermission(permissionId, LocalDateTime.now()));
    }

    /**
     * 解除 API 与当前权限的映射。
     */
    public void unbindApi(Long permissionId, Long apiId) {
        ApiEndpoint api = requireApi(apiId);
        if (permissionId.equals(api.permissionId())) {
            apiRepository.save(api.mapPermission(null, LocalDateTime.now()));
        }
    }

    /**
     * 查询映射到指定权限的 API。
     */
    @Transactional(readOnly = true)
    public List<ApiEndpoint> permissionApis(Long permissionId) {
        requirePermission(permissionId);
        return apiRepository.findByPermission(permissionId);
    }

    private PermissionNode requirePermission(Long permissionId) {
        return permissionRepository.findById(permissionId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "权限不存在: " + permissionId));
    }

    private ApiEndpoint requireApi(Long apiId) {
        return apiRepository.findById(apiId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "API 不存在: " + apiId));
    }
}
