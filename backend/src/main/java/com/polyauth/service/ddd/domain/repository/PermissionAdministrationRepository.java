package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.PermissionNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限节点管理仓储端口。
 */
public interface PermissionAdministrationRepository {

    Optional<PermissionNode> findById(Long permissionId);

    Optional<PermissionNode> findBySystemAndCode(Long systemId, String code);

    List<PermissionNode> findByIds(Collection<Long> permissionIds);

    List<PermissionNode> findBySystem(Long systemId);

    PermissionNode save(PermissionNode permission);
}
