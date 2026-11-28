package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.RoleExclusionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 角色互斥关系 Spring Data JPA Repository。
 */
public interface RoleExclusionJpaRepository
        extends JpaRepository<RoleExclusionEntity, Long> {

    Optional<RoleExclusionEntity> findByRoleIdAndExcludedRoleId(
            Long roleId, Long excludedRoleId);

    List<RoleExclusionEntity> findByRoleIdOrExcludedRoleId(
            Long roleId, Long excludedRoleId);
}
