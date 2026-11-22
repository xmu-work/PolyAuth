package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.ApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 后端 API 目录 Spring Data JPA Repository。
 */
public interface ApiJpaRepository extends JpaRepository<ApiEntity, Long> {

    Optional<ApiEntity> findBySystemIdAndApiCodeAndIsDeleted(
            Long systemId, String apiCode, Integer isDeleted);

    Optional<ApiEntity> findBySystemIdAndApiPathAndHttpMethodAndIsDeleted(
            Long systemId, String apiPath, String httpMethod, Integer isDeleted);
}
