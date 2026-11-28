package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.RoleGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户角色授权 Spring Data JPA Repository。
 */
public interface RoleGrantJpaRepository extends JpaRepository<RoleGrantEntity, Long> {

    Optional<RoleGrantEntity> findByUserIdAndRoleId(Long userId, Long roleId);

    List<RoleGrantEntity> findByUserIdOrderByIdAsc(Long userId);

    List<RoleGrantEntity> findByRoleIdOrderByIdAsc(Long roleId);
}
