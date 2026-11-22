package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 只追加审计日志 Spring Data JPA Repository。
 */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {
}
