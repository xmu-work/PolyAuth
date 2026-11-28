package com.polyauth.infrastructure.persistence.ddd.adapter;

import com.polyauth.service.ddd.domain.repository.ApiPermissionLookup;
import com.polyauth.service.ddd.domain.repository.OperationCatalogRepository;
import com.polyauth.infrastructure.persistence.ddd.entity.ApiEntity;
import com.polyauth.infrastructure.persistence.ddd.entity.SystemEntity;
import com.polyauth.infrastructure.persistence.ddd.repository.ApiJpaRepository;
import com.polyauth.infrastructure.persistence.ddd.repository.SystemJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;

/**
 * API 操作目录端口的 JPA 适配器。
 */
@Repository
public class JpaOperationCatalogRepositoryAdapter implements OperationCatalogRepository {

    private final SystemJpaRepository systemRepository;
    private final ApiJpaRepository apiRepository;

    /**
     * 注入系统与 API 只读 Repository。
     */
    public JpaOperationCatalogRepositoryAdapter(SystemJpaRepository systemRepository,
                                                 ApiJpaRepository apiRepository) {
        this.systemRepository = systemRepository;
        this.apiRepository = apiRepository;
    }

    @Override
    public Optional<ApiPermissionLookup> findByRequest(
            String apiPath, String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return Optional.empty();
        }
        return apiRepository
                .findByApiPathAndHttpMethodAndIsDeleted(
                        apiPath, httpMethod.toUpperCase(Locale.ROOT), 0)
                .filter(api -> "ACTIVE".equals(api.getStatus()))
                .map(this::lookup);
    }

    @Override
    public Optional<ApiPermissionLookup> findByOperation(
            String systemCode, String operationId) {
        return activeSystem(systemCode)
                .flatMap(system -> apiRepository
                        .findBySystemIdAndApiCodeAndIsDeleted(
                                system.getId(), operationId, 0))
                .filter(api -> "ACTIVE".equals(api.getStatus()))
                .map(this::lookup);
    }

    private Optional<SystemEntity> activeSystem(String systemCode) {
        return systemRepository.findBySystemCodeAndIsDeleted(systemCode, 0)
                .filter(system -> "ACTIVE".equals(system.getStatus()));
    }

    private ApiPermissionLookup lookup(ApiEntity api) {
        return new ApiPermissionLookup(api.getPermissionId());
    }
}
