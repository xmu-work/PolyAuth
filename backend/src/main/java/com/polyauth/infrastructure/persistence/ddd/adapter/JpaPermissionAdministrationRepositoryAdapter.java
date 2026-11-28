package com.polyauth.infrastructure.persistence.ddd.adapter;

import com.polyauth.service.ddd.domain.model.PermissionNode;
import com.polyauth.service.ddd.domain.repository.PermissionAdministrationRepository;
import com.polyauth.infrastructure.persistence.ddd.mapper.CatalogMapper;
import com.polyauth.infrastructure.persistence.ddd.repository.PermissionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限节点管理仓储端口的 JPA 适配器。
 */
@Repository
public class JpaPermissionAdministrationRepositoryAdapter
        implements PermissionAdministrationRepository {

    private final PermissionJpaRepository repository;

    /**
     * 注入权限 JPA Repository。
     */
    public JpaPermissionAdministrationRepositoryAdapter(
            PermissionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PermissionNode> findById(Long permissionId) {
        return repository.findById(permissionId).map(CatalogMapper::toDomain);
    }

    @Override
    public Optional<PermissionNode> findBySystemAndCode(Long systemId, String code) {
        return repository.findBySystemIdAndPermissionCodeAndIsDeleted(systemId, code, 0)
                .map(CatalogMapper::toDomain);
    }

    @Override
    public List<PermissionNode> findByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(permissionIds).stream()
                .map(CatalogMapper::toDomain).toList();
    }

    @Override
    public List<PermissionNode> findBySystem(Long systemId) {
        return repository.findBySystemIdAndIsDeletedOrderBySortNoAscIdAsc(systemId, 0)
                .stream().map(CatalogMapper::toDomain).toList();
    }

    @Override
    public PermissionNode save(PermissionNode permission) {
        return CatalogMapper.toDomain(
                repository.save(CatalogMapper.toEntity(permission)));
    }
}
