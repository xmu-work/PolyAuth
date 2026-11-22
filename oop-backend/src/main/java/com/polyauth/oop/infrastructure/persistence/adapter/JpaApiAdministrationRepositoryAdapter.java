package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.ApiEndpoint;
import com.polyauth.oop.domain.repository.ApiAdministrationRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.CatalogMapper;
import com.polyauth.oop.infrastructure.persistence.repository.ApiJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API 管理仓储端口的 JPA 适配器。
 */
@Repository
public class JpaApiAdministrationRepositoryAdapter
        implements ApiAdministrationRepository {

    private final ApiJpaRepository repository;

    /**
     * 注入 API JPA Repository。
     */
    public JpaApiAdministrationRepositoryAdapter(ApiJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ApiEndpoint> findById(Long apiId) {
        return repository.findById(apiId).map(CatalogMapper::toDomain);
    }

    @Override
    public Optional<ApiEndpoint> findBySystemAndCode(Long systemId, String code) {
        return repository.findBySystemIdAndApiCodeAndIsDeleted(systemId, code, 0)
                .map(CatalogMapper::toDomain);
    }

    @Override
    public Optional<ApiEndpoint> findByPathAndMethod(String path, String method) {
        return repository.findByApiPathAndHttpMethodAndIsDeleted(path, method, 0)
                .map(CatalogMapper::toDomain);
    }

    @Override
    public List<ApiEndpoint> findBySystem(Long systemId) {
        return repository.findBySystemIdAndIsDeletedOrderByIdAsc(systemId, 0)
                .stream().map(CatalogMapper::toDomain).toList();
    }

    @Override
    public List<ApiEndpoint> findByPermission(Long permissionId) {
        return repository.findByPermissionIdAndIsDeletedOrderByIdAsc(permissionId, 0)
                .stream().map(CatalogMapper::toDomain).toList();
    }

    @Override
    public ApiEndpoint save(ApiEndpoint api) {
        return CatalogMapper.toDomain(
                repository.save(CatalogMapper.toEntity(api)));
    }
}
