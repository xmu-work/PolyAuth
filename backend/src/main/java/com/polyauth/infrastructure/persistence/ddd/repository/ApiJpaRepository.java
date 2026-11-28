package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.ApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 后端 API 目录 Spring Data JPA Repository。
 */
public interface ApiJpaRepository extends JpaRepository<ApiEntity, Long> {

    Optional<ApiEntity> findBySystemIdAndApiCodeAndIsDeleted(
            Long systemId, String apiCode, Integer isDeleted);

    Optional<ApiEntity> findBySystemIdAndApiPathAndHttpMethodAndIsDeleted(
            Long systemId, String apiPath, String httpMethod, Integer isDeleted);

    Optional<ApiEntity> findByApiPathAndHttpMethodAndIsDeleted(
            String apiPath, String httpMethod, Integer isDeleted);

    List<ApiEntity> findBySystemIdAndIsDeletedOrderByIdAsc(
            Long systemId, Integer isDeleted);

    List<ApiEntity> findByPermissionIdAndIsDeletedOrderByIdAsc(
            Long permissionId, Integer isDeleted);
}
