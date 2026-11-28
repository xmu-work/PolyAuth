package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.RoleParentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 角色多继承边 Spring Data 仓储。
 */
@Repository
public interface RoleParentJpaRepository extends JpaRepository<RoleParentEntity, Long> {

    List<RoleParentEntity> findByRoleId(Long roleId);

    List<RoleParentEntity> findByRoleIdIn(Collection<Long> roleIds);

    void deleteByRoleId(Long roleId);
}
