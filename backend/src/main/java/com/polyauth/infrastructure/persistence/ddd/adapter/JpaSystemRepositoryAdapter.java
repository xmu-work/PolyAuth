package com.polyauth.infrastructure.persistence.ddd.adapter;

import com.polyauth.service.ddd.domain.model.BusinessSystem;
import com.polyauth.service.ddd.domain.repository.SystemRepository;
import com.polyauth.infrastructure.persistence.ddd.mapper.CatalogMapper;
import com.polyauth.infrastructure.persistence.ddd.repository.SystemJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业务系统仓储端口的 JPA 适配器。
 */
@Repository
public class JpaSystemRepositoryAdapter implements SystemRepository {

    private final SystemJpaRepository repository;

    /**
     * 注入系统 JPA Repository。
     */
    public JpaSystemRepositoryAdapter(SystemJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BusinessSystem> findById(Long systemId) {
        return repository.findById(systemId).map(CatalogMapper::toDomain);
    }

    @Override
    public Optional<BusinessSystem> findByCode(String code) {
        return repository.findBySystemCodeAndIsDeleted(code, 0)
                .map(CatalogMapper::toDomain);
    }

    @Override
    public List<BusinessSystem> findAll() {
        return repository.findByIsDeleted(0).stream()
                .map(CatalogMapper::toDomain).toList();
    }

    @Override
    public BusinessSystem save(BusinessSystem system) {
        return CatalogMapper.toDomain(
                repository.save(CatalogMapper.toEntity(system)));
    }
}
