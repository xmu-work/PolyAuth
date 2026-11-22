package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.RolePermissionGrant;

import java.util.Collection;
import java.util.List;

/**
 * 角色权限授予仓储端口。
 */
public interface RolePermissionRepository {

    List<RolePermissionGrant> findByRoleId(Long roleId);

    List<RolePermissionGrant> findByRoleIds(Collection<Long> roleIds);

    void bind(Long roleId, Long permissionId, OperationAction allowedAction, Long operatorId);

    void unbind(Long roleId, Long permissionId);
}
