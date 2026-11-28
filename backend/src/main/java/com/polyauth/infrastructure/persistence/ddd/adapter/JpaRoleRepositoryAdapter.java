package com.polyauth.infrastructure.persistence.ddd.adapter;

import com.polyauth.infrastructure.persistence.ddd.entity.RoleEntity;
import com.polyauth.infrastructure.persistence.ddd.entity.RoleParentEntity;
import com.polyauth.infrastructure.persistence.ddd.mapper.RoleMapper;
import com.polyauth.infrastructure.persistence.ddd.repository.RoleJpaRepository;
import com.polyauth.infrastructure.persistence.ddd.repository.RoleParentJpaRepository;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.repository.RoleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色领域端口的关系数据库适配器，负责同步多继承边。
 */
@Repository
public class JpaRoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository repository;
    private final RoleParentJpaRepository parentRepository;

    /**
     * 注入角色与继承边 Spring Data Repository。
     */
    public JpaRoleRepositoryAdapter(RoleJpaRepository repository,
                                    RoleParentJpaRepository parentRepository) {
        this.repository = repository;
        this.parentRepository = parentRepository;
    }

    @Override
    public Optional<Role> findById(Long roleId) {
        return repository.findById(roleId)
                .filter(entity -> entity.getIsDeleted() == 0)
                .map(entity -> RoleMapper.toDomain(entity, parentIdsOf(entity)));
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        return repository.findByRoleCodeAndIsDeleted(roleCode, 0)
                .map(entity -> RoleMapper.toDomain(entity, parentIdsOf(entity)));
    }

    @Override
    public List<Role> findAll() {
        List<RoleEntity> entities = repository.findByIsDeletedOrderByIdAsc(0);
        Map<Long, List<Long>> parents = parentIdsOf(entities);
        return entities.stream()
                .map(entity -> RoleMapper.toDomain(entity,
                        parents.getOrDefault(entity.getId(), legacyParentIds(entity))))
                .toList();
    }

    @Override
    @Transactional
    public Role save(Role role) {
        RoleEntity saved = repository.save(RoleMapper.toEntity(role));
        parentRepository.deleteByRoleId(saved.getId());
        parentRepository.flush();
        LocalDateTime now = LocalDateTime.now();
        List<RoleParentEntity> edges = role.parentIds().stream().map(parentId -> {
            RoleParentEntity edge = new RoleParentEntity();
            edge.setRoleId(saved.getId());
            edge.setParentId(parentId);
            edge.setCreatedAt(now);
            return edge;
        }).toList();
        if (!edges.isEmpty()) {
            parentRepository.saveAll(edges);
        }
        return RoleMapper.toDomain(saved, role.parentIds());
    }

    private List<Long> parentIdsOf(RoleEntity entity) {
        List<Long> edges = parentRepository.findByRoleId(entity.getId()).stream()
                .map(RoleParentEntity::getParentId)
                .sorted()
                .toList();
        return edges.isEmpty() ? legacyParentIds(entity) : edges;
    }

    private Map<Long, List<Long>> parentIdsOf(List<RoleEntity> entities) {
        if (entities.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new HashMap<>();
        parentRepository.findByRoleIdIn(entities.stream().map(RoleEntity::getId).toList())
                .stream()
                .sorted(java.util.Comparator.comparing(RoleParentEntity::getRoleId)
                        .thenComparing(RoleParentEntity::getParentId))
                .forEach(edge -> result.computeIfAbsent(edge.getRoleId(), key -> new ArrayList<>())
                        .add(edge.getParentId()));
        return result;
    }

    private List<Long> legacyParentIds(RoleEntity entity) {
        return entity.getParentId() == null ? List.of() : List.of(entity.getParentId());
    }
}
