package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 权限节点 Spring Data JPA Repository。
 */
public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findBySystemIdAndPermissionCodeAndIsDeleted(
            Long systemId, String permissionCode, Integer isDeleted);

    List<PermissionEntity> findBySystemIdAndIsDeletedOrderBySortNoAscIdAsc(
            Long systemId, Integer isDeleted);
}
