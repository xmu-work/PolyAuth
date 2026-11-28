package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 业务系统 Spring Data JPA Repository。
 */
public interface SystemJpaRepository extends JpaRepository<SystemEntity, Long> {

    Optional<SystemEntity> findBySystemCodeAndIsDeleted(String systemCode, Integer isDeleted);

    List<SystemEntity> findByIsDeleted(Integer isDeleted);
}
