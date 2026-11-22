package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.PermissionFact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限事实只读仓储端口。
 */
public interface PermissionCatalogRepository {

    Optional<PermissionFact> findFact(Long permissionId);

    List<PermissionFact> findFacts(Collection<Long> permissionIds);
}
