package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.ApiEndpoint;

import java.util.List;
import java.util.Optional;

/**
 * 后端 API 管理仓储端口。
 */
public interface ApiAdministrationRepository {

    Optional<ApiEndpoint> findById(Long apiId);

    Optional<ApiEndpoint> findBySystemAndCode(Long systemId, String code);

    Optional<ApiEndpoint> findByPathAndMethod(String path, String method);

    List<ApiEndpoint> findBySystem(Long systemId);

    List<ApiEndpoint> findByPermission(Long permissionId);

    ApiEndpoint save(ApiEndpoint api);
}
