package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.Organization;
import com.polyauth.oop.domain.repository.OrganizationRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.OrganizationMapper;
import com.polyauth.oop.infrastructure.persistence.repository.OrganizationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 组织聚合仓储端口的 JPA 适配器。
 */
@Repository
public class JpaOrganizationRepositoryAdapter implements OrganizationRepository {

    private final OrganizationJpaRepository repository;

    /**
     * 注入组织 Spring Data Repository。
     */
    public JpaOrganizationRepositoryAdapter(OrganizationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Organization> findById(Long orgId) {
        return repository.findById(orgId).map(OrganizationMapper::toDomain);
    }

    @Override
    public Optional<Organization> findByCode(String orgCode) {
        return repository.findByOrgCode(orgCode).map(OrganizationMapper::toDomain);
    }

    @Override
    public List<Organization> findRoots() {
        return repository.findByParentIdIsNullAndIsDeletedOrderBySortNoAscIdAsc(0)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public List<Organization> findByParentId(Long parentId) {
        return repository.findByParentIdAndIsDeletedOrderBySortNoAscIdAsc(parentId, 0)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public List<Organization> findByAncestorsPrefix(String prefix) {
        return repository.findByAncestorsStartingWithAndIsDeleted(prefix, 0)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public Organization save(Organization organization) {
        return OrganizationMapper.toDomain(
                repository.save(OrganizationMapper.toEntity(organization)));
    }

    @Override
    public List<Organization> saveAll(List<Organization> organizations) {
        return repository.saveAll(organizations.stream()
                        .map(OrganizationMapper::toEntity)
                        .toList())
                .stream().map(OrganizationMapper::toDomain).toList();
    }
}
