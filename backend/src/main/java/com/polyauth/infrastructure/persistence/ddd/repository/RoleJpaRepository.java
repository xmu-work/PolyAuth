package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 角色 Spring Data JPA Repository，仅由 Adapter 使用。
 */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    List<RoleEntity> findByIsDeletedOrderByIdAsc(Integer isDeleted);
}
