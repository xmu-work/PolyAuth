package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 角色权限绑定 Spring Data JPA Repository。
 */
public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, Long> {

    List<RolePermissionEntity> findByRoleIdOrderByIdAsc(Long roleId);

    List<RolePermissionEntity> findByRoleIdIn(Collection<Long> roleIds);

    Optional<RolePermissionEntity> findByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
