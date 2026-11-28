package com.polyauth.service.ddd.adapter.role;

import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import com.polyauth.infrastructure.persistence.structured.entity.IamRolePermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;
import com.polyauth.service.ddd.application.command.CreateRoleCommand;
import com.polyauth.service.ddd.application.command.GrantRoleCommand;
import com.polyauth.service.ddd.application.command.UpdateRoleCommand;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.RoleGrantStatus;
import com.polyauth.service.ddd.domain.model.RolePermissionGrant;
import com.polyauth.service.ddd.domain.repository.RoleGrantRepository;
import com.polyauth.service.port.role.RoleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 角色治理用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleService implements RoleUseCase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final com.polyauth.service.ddd.application.usecase.RoleManagementUseCase useCase;
    private final RoleGrantRepository roleGrantRepository;

    @Override
    public IamRole createRole(String roleCode, String roleName, String roleType, String description) {
        return createRole(roleCode, roleName, roleType, description, List.of());
    }

    @Override
    public IamRole createRole(String roleCode, String roleName, String roleType, String description,
                              Long parentId) {
        return createRole(roleCode, roleName, roleType, description,
                parentId == null ? List.of() : List.of(parentId));
    }

    @Override
    public IamRole createRole(String roleCode, String roleName, String roleType, String description,
                              List<Long> parentIds) {
        return toEntity(useCase.createRole(new CreateRoleCommand(
                roleCode, roleName, roleType, description, parentIds, null)));
    }

    @Override
    public IamRole updateRole(Long roleId, String roleName, String description, String status,
                              Long parentId, Long operatorId) {
        return updateRole(roleId, roleName, description, status,
                parentId == null ? List.of() : List.of(parentId), operatorId);
    }

    @Override
    public IamRole updateRole(Long roleId, String roleName, String description, String status,
                              List<Long> parentIds, Long operatorId) {
        return toEntity(useCase.updateRole(new UpdateRoleCommand(
                roleId, roleName, description, status, parentIds, operatorId)));
    }

    @Override
    public List<Long> getParentIds(Long roleId) {
        return useCase.getRole(roleId).parentIds();
    }

    @Override
    public Map<Long, List<Long>> getParentIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        return useCase.listRoles().stream()
                .filter(role -> roleIds.contains(role.id()))
                .collect(java.util.stream.Collectors.toMap(
                        Role::id, Role::parentIds, (left, right) -> left));
    }

    @Override
    public void bindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId) {
        useCase.bindExclusion(roleId, excludedRoleId, operatorId);
    }

    @Override
    public void unbindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId) {
        useCase.unbindExclusion(roleId, excludedRoleId, operatorId);
    }

    @Override
    public List<Long> listRoleExclusions(Long roleId) {
        return useCase.exclusions(roleId);
    }

    @Override
    public IamRole getRole(Long roleId) {
        return toEntity(useCase.getRole(roleId));
    }

    @Override
    public List<IamRole> listRoles() {
        return useCase.listRoles().stream().map(this::toEntity).toList();
    }

    @Override
    public IamUserRole grantRole(Long userId, Long roleId, LocalDateTime expireTime,
                                 Long grantBy, String grantReason) {
        Instant expire = expireTime == null ? null : expireTime.atZone(ZONE).toInstant();
        return toGrant(useCase.grantRole(new GrantRoleCommand(
                userId, roleId, expire, grantBy, grantReason)));
    }

    @Override
    public void grantRoles(Long userId, List<Long> roleIds, LocalDateTime expireTime,
                           Long grantBy, String grantReason) {
        for (Long roleId : roleIds) {
            grantRole(userId, roleId, expireTime, grantBy, grantReason);
        }
    }

    @Override
    public void revokeRole(Long userId, Long roleId, Long operatorId) {
        useCase.revokeRole(userId, roleId, operatorId);
    }

    @Override
    public void revokeAllRoles(Long userId) {
        revokeAllRoles(userId, null);
    }

    @Override
    public void revokeAllRoles(Long userId, Long operatorId) {
        roleGrantRepository.findByUserId(userId).stream()
                .filter(grant -> grant.status() == RoleGrantStatus.ACTIVE)
                .forEach(grant -> useCase.revokeRole(userId, grant.roleId(), operatorId));
    }

    @Override
    public List<IamUserRole> getUserRoles(Long userId) {
        return useCase.userAssignments(userId).stream().map(this::toGrant).toList();
    }

    @Override
    public List<IamUserRole> getRoleUsers(Long roleId) {
        return useCase.roleAssignments(roleId).stream().map(this::toGrant).toList();
    }

    @Override
    public void bindRolePermissions(Long roleId, List<Long> permissionIds,
                                    String allowedAction, Long createdBy) {
        useCase.bindPermissions(roleId, permissionIds, allowedAction, createdBy);
    }

    @Override
    public void unbindRolePermissions(Long roleId, List<Long> permissionIds, Long operatorId) {
        useCase.unbindPermissions(roleId, permissionIds, operatorId);
    }

    @Override
    public List<IamRolePermission> getRolePermissions(Long roleId) {
        return useCase.rolePermissions(roleId).stream().map(this::toRolePermission).toList();
    }

    private IamRole toEntity(Role role) {
        IamRole entity = new IamRole();
        entity.setId(role.id());
        entity.setRoleCode(role.roleCode());
        entity.setRoleName(role.roleName());
        entity.setRoleType(role.roleType() == null ? null : role.roleType().name());
        entity.setParentId(role.parentId());
        entity.setStatus(role.status() == null ? null : role.status().name());
        entity.setDescription(role.description());
        entity.setIsDeleted(0);
        entity.setCreatedAt(role.createdAt());
        entity.setUpdatedAt(role.updatedAt());
        entity.setVersion(role.version());
        entity.setCreatedBy(role.createdBy());
        entity.setUpdatedBy(role.updatedBy());
        return entity;
    }

    private IamUserRole toGrant(RoleGrant grant) {
        IamUserRole entity = new IamUserRole();
        entity.setId(grant.id());
        entity.setUserId(grant.userId());
        entity.setRoleId(grant.roleId());
        entity.setEffectiveTime(local(grant.effectiveTime()));
        entity.setExpireTime(local(grant.expireTime()));
        entity.setGrantBy(grant.grantBy());
        entity.setGrantReason(grant.reason());
        entity.setStatus(grant.status() == null ? null : grant.status().name());
        entity.setCreatedAt(local(grant.createdAt()));
        entity.setUpdatedAt(local(grant.updatedAt()));
        entity.setVersion(grant.version());
        return entity;
    }

    private IamRolePermission toRolePermission(RolePermissionGrant grant) {
        IamRolePermission entity = new IamRolePermission();
        entity.setId(grant.id());
        entity.setRoleId(grant.roleId());
        entity.setPermissionId(grant.permissionId());
        entity.setAllowedAction(grant.allowedAction() == null ? null : grant.allowedAction().name());
        entity.setCreatedAt(grant.createdAt());
        entity.setCreatedBy(grant.createdBy());
        return entity;
    }

    private LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }
}
