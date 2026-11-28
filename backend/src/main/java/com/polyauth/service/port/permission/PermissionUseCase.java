package com.polyauth.service.port.permission;

import com.polyauth.infrastructure.persistence.structured.entity.IamApi;
import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;

import java.util.List;

/**
 * 前端权限树与 API 映射用例契约。
 */
public interface PermissionUseCase {

    IamPermission createPermission(Long systemId, Long parentId, String permissionCode,
                                   String permissionName, String permissionType,
                                   String routePath, String action, Integer sortNo);

    IamPermission getPermission(Long permissionId);

    List<IamPermission> listPermissions(Long systemId);

    IamApi bindApi(Long permissionId, Long apiId);

    void unbindApi(Long permissionId, Long apiId);

    List<IamApi> getPermissionApis(Long permissionId);
}
