package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 只追加审计日志 Spring Data JPA Repository。
 */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long>,
        JpaSpecificationExecutor<AuditLogEntity> {

    Optional<AuditLogEntity> findFirstBySystemCodeAndDecisionIdAndRequestId(
            String systemCode, String decisionId, String requestId);
}
