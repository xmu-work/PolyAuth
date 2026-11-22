package com.polyauth.oop.infrastructure.persistence.mapper;

import com.polyauth.oop.domain.model.ApiEndpoint;
import com.polyauth.oop.domain.model.BusinessSystem;
import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.PermissionNode;
import com.polyauth.oop.infrastructure.persistence.entity.ApiEntity;
import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import com.polyauth.oop.infrastructure.persistence.entity.SystemEntity;

/**
 * 权限目录领域对象与数据库模型映射器。
 */
public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static BusinessSystem toDomain(SystemEntity entity) {
        return BusinessSystem.restore(entity.getId(), entity.getSystemCode(),
                entity.getSystemName(), entity.getCallbackUrl(), entity.getAppSecret(),
                entity.getStatus(),
                entity.getDescription(), entity.getIsDeleted() != 0,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }

    public static SystemEntity toEntity(BusinessSystem system) {
        SystemEntity entity = new SystemEntity();
        entity.setId(system.id());
        entity.setSystemCode(system.systemCode());
        entity.setSystemName(system.systemName());
        entity.setCallbackUrl(system.callbackUrl());
        entity.setAppSecret(system.appSecret());
        entity.setStatus(system.status());
        entity.setDescription(system.description());
        entity.setIsDeleted(system.deleted() ? 1 : 0);
        entity.setCreatedAt(system.createdAt());
        entity.setUpdatedAt(system.updatedAt());
        entity.setVersion(system.version());
        return entity;
    }

    public static PermissionNode toDomain(PermissionEntity entity) {
        return PermissionNode.restore(entity.getId(), entity.getSystemId(),
                entity.getParentId(), entity.getPermissionCode(),
                entity.getPermissionName(), entity.getPermissionType(),
                entity.getRoutePath(), OperationAction.valueOf(entity.getAction()),
                entity.getSortNo(), entity.getStatus(), entity.getIsDeleted() != 0,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static PermissionEntity toEntity(PermissionNode node) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(node.id());
        entity.setSystemId(node.systemId());
        entity.setParentId(node.parentId());
        entity.setPermissionCode(node.permissionCode());
        entity.setPermissionName(node.permissionName());
        entity.setPermissionType(node.permissionType());
        entity.setRoutePath(node.routePath());
        entity.setAction(node.action().name());
        entity.setSortNo(node.sortNo());
        entity.setStatus(node.status());
        entity.setIsDeleted(node.deleted() ? 1 : 0);
        entity.setCreatedAt(node.createdAt());
        entity.setUpdatedAt(node.updatedAt());
        return entity;
    }

    public static ApiEndpoint toDomain(ApiEntity entity) {
        return ApiEndpoint.restore(entity.getId(), entity.getSystemId(),
                entity.getPermissionId(), entity.getApiCode(), entity.getApiName(),
                entity.getApiPath(), entity.getHttpMethod(), entity.getStatus(),
                entity.getIsDeleted() != 0, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static ApiEntity toEntity(ApiEndpoint api) {
        ApiEntity entity = new ApiEntity();
        entity.setId(api.id());
        entity.setSystemId(api.systemId());
        entity.setPermissionId(api.permissionId());
        entity.setApiCode(api.apiCode());
        entity.setApiName(api.apiName());
        entity.setApiPath(api.apiPath());
        entity.setHttpMethod(api.httpMethod());
        entity.setStatus(api.status());
        entity.setIsDeleted(api.deleted() ? 1 : 0);
        entity.setCreatedAt(api.createdAt());
        entity.setUpdatedAt(api.updatedAt());
        return entity;
    }
}
