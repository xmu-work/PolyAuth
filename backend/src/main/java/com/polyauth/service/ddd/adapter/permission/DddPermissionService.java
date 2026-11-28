package com.polyauth.service.ddd.adapter.permission;

import com.polyauth.infrastructure.persistence.structured.entity.IamApi;
import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;
import com.polyauth.service.ddd.domain.model.ApiEndpoint;
import com.polyauth.service.ddd.domain.model.PermissionNode;
import com.polyauth.service.port.permission.PermissionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前端权限树与 API 映射用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddPermissionService implements PermissionUseCase {

    private final com.polyauth.service.ddd.application.usecase.PermissionCatalogUseCase useCase;

    @Override
    public IamPermission createPermission(Long systemId, Long parentId, String permissionCode,
                                          String permissionName, String permissionType,
                                          String routePath, String action, Integer sortNo) {
        return toPermission(useCase.create(systemId, parentId, permissionCode, permissionName,
                permissionType, routePath, action, sortNo));
    }

    @Override
    public IamPermission getPermission(Long permissionId) {
        throw new UnsupportedOperationException("DDD 未提供权限详情端点的独立用例");
    }

    @Override
    public List<IamPermission> listPermissions(Long systemId) {
        return useCase.list(systemId).stream().map(this::toPermission).toList();
    }

    @Override
    public IamApi bindApi(Long permissionId, Long apiId) {
        return toApi(useCase.bindApi(permissionId, apiId));
    }

    @Override
    public void unbindApi(Long permissionId, Long apiId) {
        useCase.unbindApi(permissionId, apiId);
    }

    @Override
    public List<IamApi> getPermissionApis(Long permissionId) {
        return useCase.permissionApis(permissionId).stream().map(this::toApi).toList();
    }

    private IamPermission toPermission(PermissionNode node) {
        IamPermission entity = new IamPermission();
        entity.setId(node.id());
        entity.setSystemId(node.systemId());
        entity.setParentId(node.parentId());
        entity.setPermissionCode(node.permissionCode());
        entity.setPermissionName(node.permissionName());
        entity.setPermissionType(node.permissionType());
        entity.setRoutePath(node.routePath());
        entity.setAction(node.action() == null ? null : node.action().name());
        entity.setSortNo(node.sortNo());
        entity.setStatus(node.status());
        entity.setIsDeleted(node.deleted() ? 1 : 0);
        entity.setCreatedAt(node.createdAt());
        entity.setUpdatedAt(node.updatedAt());
        return entity;
    }

    private IamApi toApi(ApiEndpoint endpoint) {
        IamApi entity = new IamApi();
        entity.setId(endpoint.id());
        entity.setSystemId(endpoint.systemId());
        entity.setPermissionId(endpoint.permissionId());
        entity.setApiCode(endpoint.apiCode());
        entity.setApiName(endpoint.apiName());
        entity.setApiPath(endpoint.apiPath());
        entity.setHttpMethod(endpoint.httpMethod());
        entity.setStatus(endpoint.status());
        entity.setIsDeleted(endpoint.deleted() ? 1 : 0);
        entity.setCreatedAt(endpoint.createdAt());
        entity.setUpdatedAt(endpoint.updatedAt());
        return entity;
    }
}
