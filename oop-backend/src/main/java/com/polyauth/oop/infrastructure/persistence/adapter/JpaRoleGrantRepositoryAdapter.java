package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.repository.RoleGrantRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.RoleGrantMapper;
import com.polyauth.oop.infrastructure.persistence.repository.RoleGrantJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户角色授权领域端口的 JPA 适配器。
 */
@Repository
public class JpaRoleGrantRepositoryAdapter implements RoleGrantRepository {

    private final RoleGrantJpaRepository repository;

    /**
     * 注入角色授权 Spring Data Repository。
     */
    public JpaRoleGrantRepositoryAdapter(RoleGrantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RoleGrant> findByUserIdAndRoleId(Long userId, Long roleId) {
        return repository.findByUserIdAndRoleId(userId, roleId)
                .map(RoleGrantMapper::toDomain);
    }

    @Override
    public List<RoleGrant> findByUserId(Long userId) {
        return repository.findByUserIdOrderByIdAsc(userId).stream()
                .map(RoleGrantMapper::toDomain)
                .toList();
    }

    @Override
    public List<RoleGrant> findByRoleId(Long roleId) {
        return repository.findByRoleIdOrderByIdAsc(roleId).stream()
                .map(RoleGrantMapper::toDomain)
                .toList();
    }

    @Override
    public RoleGrant save(RoleGrant grant) {
        return RoleGrantMapper.toDomain(repository.save(RoleGrantMapper.toEntity(grant)));
    }
}
