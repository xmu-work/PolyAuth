package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 业务系统 Spring Data JPA Repository。
 */
public interface SystemJpaRepository extends JpaRepository<SystemEntity, Long> {

    Optional<SystemEntity> findBySystemCodeAndIsDeleted(String systemCode, Integer isDeleted);
}
