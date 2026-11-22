package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.RolePermissionGrant;
import com.polyauth.oop.domain.repository.RolePermissionRepository;
import com.polyauth.oop.infrastructure.persistence.entity.RolePermissionEntity;
import com.polyauth.oop.infrastructure.persistence.repository.RolePermissionJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 角色权限绑定领域端口的 JPA 适配器。
 */
@Repository
public class JpaRolePermissionRepositoryAdapter implements RolePermissionRepository {

    private final RolePermissionJpaRepository repository;

    /**
     * 注入角色权限绑定 Spring Data Repository。
     */
    public JpaRolePermissionRepositoryAdapter(RolePermissionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RolePermissionGrant> findByRoleId(Long roleId) {
        return repository.findByRoleIdOrderByIdAsc(roleId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RolePermissionGrant> findByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return repository.findByRoleIdIn(roleIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void bind(Long roleId, Long permissionId,
                     OperationAction allowedAction, Long operatorId) {
        if (repository.findByRoleIdAndPermissionId(roleId, permissionId).isPresent()) {
            return;
        }
        RolePermissionEntity entity = new RolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionId(permissionId);
        entity.setAllowedAction(allowedAction.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(operatorId);
        repository.save(entity);
    }

    @Override
    public void unbind(Long roleId, Long permissionId) {
        repository.findByRoleIdAndPermissionId(roleId, permissionId)
                .ifPresent(repository::delete);
    }

    private RolePermissionGrant toDomain(RolePermissionEntity entity) {
        return new RolePermissionGrant(entity.getId(), entity.getRoleId(),
                entity.getPermissionId(),
                OperationAction.valueOf(entity.getAllowedAction()),
                entity.getCreatedAt(), entity.getCreatedBy());
    }
}
