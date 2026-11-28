package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.PermissionFact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限事实只读仓储端口。
 */
public interface PermissionCatalogRepository {

    Optional<PermissionFact> findFact(Long permissionId);

    List<PermissionFact> findFacts(Collection<Long> permissionIds);

    /**
     * 查询目标系统下所有只读权限（action=VIEW）的判定事实。
     */
    List<PermissionFact> findViewFacts(String systemCode);
}
