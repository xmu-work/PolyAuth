package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 权限节点 Spring Data JPA Repository。
 */
public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {
}
