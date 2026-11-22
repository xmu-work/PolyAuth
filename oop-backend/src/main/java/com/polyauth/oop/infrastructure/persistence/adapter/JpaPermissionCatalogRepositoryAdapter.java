package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.PermissionFact;
import com.polyauth.oop.domain.repository.PermissionCatalogRepository;
import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import com.polyauth.oop.infrastructure.persistence.entity.SystemEntity;
import com.polyauth.oop.infrastructure.persistence.repository.PermissionJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.SystemJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限事实只读查询的 JPA 适配器。
 */
@Repository
public class JpaPermissionCatalogRepositoryAdapter implements PermissionCatalogRepository {

    private final PermissionJpaRepository permissionRepository;
    private final SystemJpaRepository systemRepository;

    /**
     * 注入权限与系统只读 Repository。
     */
    public JpaPermissionCatalogRepositoryAdapter(PermissionJpaRepository permissionRepository,
                                                  SystemJpaRepository systemRepository) {
        this.permissionRepository = permissionRepository;
        this.systemRepository = systemRepository;
    }

    @Override
    public Optional<PermissionFact> findFact(Long permissionId) {
        return permissionRepository.findById(permissionId).flatMap(this::toFact);
    }

    @Override
    public List<PermissionFact> findFacts(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        List<PermissionEntity> permissions = permissionRepository.findAllById(permissionIds);
        Map<Long, SystemEntity> systems = systemRepository.findAllById(
                        permissions.stream().map(PermissionEntity::getSystemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SystemEntity::getId, Function.identity()));
        return permissions.stream()
                .map(permission -> toFact(permission, systems.get(permission.getSystemId())))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PermissionFact> toFact(PermissionEntity permission) {
        return systemRepository.findById(permission.getSystemId())
                .flatMap(system -> toFact(permission, system));
    }

    private Optional<PermissionFact> toFact(PermissionEntity permission, SystemEntity system) {
        if (system == null || system.getIsDeleted() != 0
                || !"ACTIVE".equals(system.getStatus())
                || permission.getIsDeleted() != 0
                || !"ACTIVE".equals(permission.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new PermissionFact(
                permission.getId(),
                system.getSystemCode(),
                OperationAction.valueOf(permission.getAction()),
                true));
    }
}
