package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.repository.RoleRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.RoleMapper;
import com.polyauth.oop.infrastructure.persistence.repository.RoleJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色领域端口的关系数据库适配器。
 */
@Repository
public class JpaRoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository repository;

    /**
     * 注入仅供基础设施使用的 Spring Data Repository。
     */
    public JpaRoleRepositoryAdapter(RoleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Role> findById(Long roleId) {
        return repository.findById(roleId)
                .filter(entity -> entity.getIsDeleted() == 0)
                .map(RoleMapper::toDomain);
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        return repository.findByRoleCodeAndIsDeleted(roleCode, 0)
                .map(RoleMapper::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return repository.findByIsDeletedOrderByIdAsc(0).stream()
                .map(RoleMapper::toDomain)
                .toList();
    }

    @Override
    public Role save(Role role) {
        return RoleMapper.toDomain(repository.save(RoleMapper.toEntity(role)));
    }
}
