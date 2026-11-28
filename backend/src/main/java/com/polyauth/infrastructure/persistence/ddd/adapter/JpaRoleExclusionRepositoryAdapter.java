package com.polyauth.infrastructure.persistence.ddd.adapter;

import com.polyauth.service.ddd.domain.model.RoleExclusion;
import com.polyauth.service.ddd.domain.repository.RoleExclusionRepository;
import com.polyauth.infrastructure.persistence.ddd.entity.RoleExclusionEntity;
import com.polyauth.infrastructure.persistence.ddd.repository.RoleExclusionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色互斥仓储端口的 JPA 适配器。
 */
@Repository
public class JpaRoleExclusionRepositoryAdapter
        implements RoleExclusionRepository {

    private final RoleExclusionJpaRepository repository;

    /**
     * 注入互斥关系 JPA Repository。
     */
    public JpaRoleExclusionRepositoryAdapter(RoleExclusionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RoleExclusion> findPair(Long firstRoleId, Long secondRoleId) {
        Long first = Math.min(firstRoleId, secondRoleId);
        Long second = Math.max(firstRoleId, secondRoleId);
        return repository.findByRoleIdAndExcludedRoleId(first, second)
                .map(this::toDomain);
    }

    @Override
    public List<RoleExclusion> findByRole(Long roleId) {
        return repository.findByRoleIdOrExcludedRoleId(roleId, roleId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public RoleExclusion save(RoleExclusion exclusion) {
        RoleExclusionEntity entity = new RoleExclusionEntity();
        entity.setId(exclusion.id());
        entity.setRoleId(exclusion.roleId());
        entity.setExcludedRoleId(exclusion.excludedRoleId());
        entity.setCreatedBy(exclusion.createdBy());
        entity.setCreatedAt(exclusion.createdAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public void delete(Long exclusionId) {
        repository.deleteById(exclusionId);
    }

    private RoleExclusion toDomain(RoleExclusionEntity entity) {
        return new RoleExclusion(entity.getId(), entity.getRoleId(),
                entity.getExcludedRoleId(), entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
