package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.command.CreateRoleCommand;
import com.polyauth.oop.application.command.GrantRoleCommand;
import com.polyauth.oop.application.command.UpdateRoleCommand;
import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.domain.model.AccountStatus;
import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.PermissionFact;
import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RolePermissionGrant;
import com.polyauth.oop.domain.model.RoleStatus;
import com.polyauth.oop.domain.model.RoleType;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.PermissionCatalogRepository;
import com.polyauth.oop.domain.repository.RoleGrantRepository;
import com.polyauth.oop.domain.repository.RolePermissionRepository;
import com.polyauth.oop.domain.repository.RoleRepository;
import com.polyauth.oop.domain.repository.RoleExclusionRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import com.polyauth.oop.domain.service.RoleHierarchy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色管理用例编排，负责事务边界之外的领域调用顺序。
 */
@Service
@Transactional
public class RoleManagementUseCase {

    private static final String ADMIN_SYSTEM_CODE = "ADMIN";
    private static final Function<Role, Map<String, Object>> ROLE_SNAPSHOT = role -> {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleId", role.id());
        snapshot.put("roleCode", role.roleCode());
        snapshot.put("roleName", role.roleName());
        snapshot.put("roleType", role.roleType().name());
        snapshot.put("parentId", role.parentId());
        snapshot.put("status", role.status().name());
        snapshot.put("description", role.description());
        return snapshot;
    };
    private static final Function<RoleGrant, Map<String, Object>> GRANT_SNAPSHOT = grant -> {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("grantId", grant.id());
        snapshot.put("userId", grant.userId());
        snapshot.put("roleId", grant.roleId());
        snapshot.put("effectiveTime", grant.effectiveTime());
        snapshot.put("expireTime", grant.expireTime());
        snapshot.put("status", grant.status().name());
        snapshot.put("grantReason", grant.reason());
        return snapshot;
    };

    private final RoleRepository roleRepository;
    private final UserAccountRepository userRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionCatalogRepository permissionCatalogRepository;
    private final RoleExclusionRepository roleExclusionRepository;
    private final PermissionAccessUseCase permissionAccessUseCase;
    private final AuditTrail auditTrail;
    private final Clock clock;

    /**
     * 注入领域端口和可控时钟。
     */
    public RoleManagementUseCase(RoleRepository roleRepository,
                                 UserAccountRepository userRepository,
                                 RoleGrantRepository roleGrantRepository,
                                 RolePermissionRepository rolePermissionRepository,
                                 PermissionCatalogRepository permissionCatalogRepository,
                                 RoleExclusionRepository roleExclusionRepository,
                                 PermissionAccessUseCase permissionAccessUseCase,
                                 AuditTrail auditTrail,
                                 Clock clock) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionCatalogRepository = permissionCatalogRepository;
        this.roleExclusionRepository = roleExclusionRepository;
        this.permissionAccessUseCase = permissionAccessUseCase;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /**
     * 创建角色。
     */
    public Role createRole(CreateRoleCommand command) {
        String roleCode = requireText(command.roleCode(), "角色编码不能为空");
        if (roleRepository.findByCode(roleCode).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "角色编码已存在: " + roleCode);
        }
        RoleType roleType = parseRoleType(command.roleType());
        validateParent(command.parentId());
        Role role = Role.create(roleCode, command.roleName(), roleType, command.description(),
                command.parentId(), command.operatorId(), now());
        Role saved = roleRepository.save(role);
        auditTrail.recordPermissionChange(command.operatorId(), ADMIN_SYSTEM_CODE,
                "CREATE_ROLE", "role:" + saved.id(), null, ROLE_SNAPSHOT.apply(saved));
        return saved;
    }

    /**
     * 更新角色。
     */
    public Role updateRole(UpdateRoleCommand command) {
        Role role = requireRole(command.roleId());
        RoleStatus status = parseStatus(command.status());
        validateParent(command.parentId());
        Map<String, Object> before = ROLE_SNAPSHOT.apply(role);
        try {
            RoleHierarchy.ensureNoCycle(
                    command.roleId(), command.parentId(), roleRepository::findById);
            role.update(command.roleName(), command.description(), status,
                    command.parentId(), command.operatorId(), now());
        } catch (com.polyauth.oop.domain.exception.DomainRuleException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.ROLE_INHERIT_CYCLE, exception.getMessage());
        }
        Role saved = roleRepository.save(role);
        auditTrail.recordPermissionChange(command.operatorId(), ADMIN_SYSTEM_CODE,
                "UPDATE_ROLE", "role:" + saved.id(), before, ROLE_SNAPSHOT.apply(saved));
        return saved;
    }

    /**
     * 授予用户角色。
     */
    public RoleGrant grantRole(GrantRoleCommand command) {
        UserAccount user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        if (!user.canAuthenticate()) {
            throw new UseCaseException(
                    ApplicationErrorCode.TARGET_USER_ABNORMAL,
                    "用户状态不允许授权");
        }
        Role role = requireRole(command.roleId());
        if (!role.isActive()) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "角色已停用");
        }
        ensureNoExclusion(command.userId(), command.roleId());
        checkGrantBoundary(command.grantBy(), command.roleId());
        Instant now = clock.instant();
        if (command.expireTime() != null && !command.expireTime().isAfter(now)) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "失效时间必须晚于当前时间");
        }
        Optional<RoleGrant> existing = roleGrantRepository.findByUserIdAndRoleId(
                command.userId(), command.roleId());
        if (existing.filter(grant -> grant.status().name().equals("ACTIVE")).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "重复授权");
        }
        RoleGrant grant = existing
                .map(item -> item.reactivate(now, command.expireTime(),
                        command.grantBy(), command.reason(), now))
                .orElseGet(() -> RoleGrant.grant(
                        command.userId(), command.roleId(), now,
                        command.expireTime(), command.grantBy(),
                        command.reason(), now));
        RoleGrant saved = roleGrantRepository.save(grant);
        auditTrail.recordPermissionChange(command.grantBy(), ADMIN_SYSTEM_CODE,
                "GRANT_ROLE", "user:" + command.userId() + "/role:" + command.roleId(),
                existing.map(GRANT_SNAPSHOT).orElse(null), GRANT_SNAPSHOT.apply(saved));
        return saved;
    }

    /**
     * 查询全部未删除角色。
     */
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 查询单个角色。
     */
    public Role getRole(Long roleId) {
        return requireRole(roleId);
    }

    /**
     * 回收用户角色授权。
     */
    public void revokeRole(Long userId, Long roleId, Long operatorId) {
        RoleGrant grant = roleGrantRepository.findByUserIdAndRoleId(userId, roleId)
                .filter(item -> item.status().name().equals("ACTIVE"))
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND, "用户未被授予该角色"));
        Map<String, Object> before = GRANT_SNAPSHOT.apply(grant);
        grant.revoke(clock.instant());
        RoleGrant saved = roleGrantRepository.save(grant);
        auditTrail.recordPermissionChange(operatorId, ADMIN_SYSTEM_CODE,
                "REVOKE_ROLE", "user:" + userId + "/role:" + roleId,
                before, GRANT_SNAPSHOT.apply(saved));
    }

    /**
     * 查询用户的角色授权。
     */
    public List<RoleGrant> userAssignments(Long userId) {
        return roleGrantRepository.findByUserId(userId);
    }

    /**
     * 查询角色下的用户分配。
     */
    public List<RoleGrant> roleAssignments(Long roleId) {
        requireRole(roleId);
        return roleGrantRepository.findByRoleId(roleId);
    }

    /**
     * 为角色授予前端菜单或按钮权限。
     */
    public void bindPermissions(Long roleId, List<Long> permissionIds,
                                String allowedAction, Long operatorId) {
        Role role = requireRole(roleId);
        if (!role.isActive()) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "角色已停用");
        }
        if (permissionIds == null || permissionIds.isEmpty() || permissionIds.size() > 200) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "权限列表不能为空且不能超过 200 项");
        }
        OperationAction action = parseOperationAction(allowedAction);
        List<Long> before = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionGrant::permissionId)
                .sorted()
                .toList();
        List<PermissionFact> facts = permissionCatalogRepository.findFacts(permissionIds);
        for (Long permissionId : permissionIds) {
            PermissionFact fact = facts.stream()
                    .filter(item -> item.permissionId().equals(permissionId))
                    .findFirst()
                    .orElseThrow(() -> new UseCaseException(
                            ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "权限不存在: " + permissionId));
            if (!fact.active()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID, "权限已停用: " + permissionId);
            }
        }
        permissionIds.stream().distinct()
                .forEach(permissionId -> rolePermissionRepository.bind(
                        roleId, permissionId, action, operatorId));
        List<Long> after = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionGrant::permissionId)
                .sorted()
                .toList();
        auditTrail.recordPermissionChange(operatorId, ADMIN_SYSTEM_CODE,
                "BIND_ROLE_PERMISSION", "role:" + roleId,
                Map.of("roleId", roleId, "permissionIds", before),
                Map.of("roleId", roleId, "permissionIds", after));
    }

    /**
     * 查询角色直接绑定的前端权限。
     */
    public List<RolePermissionGrant> rolePermissions(Long roleId) {
        requireRole(roleId);
        return rolePermissionRepository.findByRoleId(roleId);
    }

    /**
     * 解除角色与前端权限绑定。
     */
    public void unbindPermissions(Long roleId, List<Long> permissionIds,
                                  Long operatorId) {
        requireRole(roleId);
        if (permissionIds == null || permissionIds.isEmpty()
                || permissionIds.size() > 200) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "权限列表不能为空且不能超过 200 项");
        }
        List<Long> before = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionGrant::permissionId).sorted().toList();
        permissionIds.stream().distinct()
                .forEach(permissionId -> rolePermissionRepository.unbind(
                        roleId, permissionId));
        List<Long> after = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermissionGrant::permissionId).sorted().toList();
        auditTrail.recordPermissionChange(operatorId, ADMIN_SYSTEM_CODE,
                "UNBIND_ROLE_PERMISSION", "role:" + roleId,
                Map.of("roleId", roleId, "permissionIds", before),
                Map.of("roleId", roleId, "permissionIds", after));
    }

    /**
     * 查询角色互斥关系。
     */
    public List<Long> exclusions(Long roleId) {
        requireRole(roleId);
        return roleExclusionRepository.findByRole(roleId).stream()
                .map(item -> item.peerOf(roleId))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 新建角色互斥关系。
     */
    public void bindExclusion(Long roleId, Long excludedRoleId, Long operatorId) {
        if (roleId.equals(excludedRoleId)) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "互斥角色不能相同");
        }
        requireRole(roleId);
        requireRole(excludedRoleId);
        if (roleExclusionRepository.findPair(roleId, excludedRoleId).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "角色互斥关系已存在");
        }
        Set<Long> firstUsers = roleGrantRepository.findByRoleId(roleId).stream()
                .filter(grant -> grant.status().name().equals("ACTIVE"))
                .filter(grant -> grant.isEffective(clock.instant()))
                .map(RoleGrant::userId)
                .collect(java.util.stream.Collectors.toSet());
        boolean conflict = roleGrantRepository.findByRoleId(excludedRoleId).stream()
                .filter(grant -> grant.status().name().equals("ACTIVE"))
                .filter(grant -> grant.isEffective(clock.instant()))
                .anyMatch(grant -> firstUsers.contains(grant.userId()));
        if (conflict) {
            throw new UseCaseException(
                    ApplicationErrorCode.ROLE_EXCLUSION_CONFLICT,
                    "已有用户同时拥有这两个角色，请先回收冲突授权");
        }
        com.polyauth.oop.domain.model.RoleExclusion saved =
                roleExclusionRepository.save(
                        com.polyauth.oop.domain.model.RoleExclusion.create(
                                roleId, excludedRoleId, operatorId, now()));
        auditTrail.recordPermissionChange(operatorId, ADMIN_SYSTEM_CODE,
                "BIND_ROLE_EXCLUSION",
                "role:" + saved.roleId() + "/exclude:" + saved.excludedRoleId(),
                null, Map.of("roleId", saved.roleId(),
                        "excludedRoleId", saved.excludedRoleId()));
    }

    /**
     * 删除角色互斥关系。
     */
    public void unbindExclusion(Long roleId, Long excludedRoleId,
                                Long operatorId) {
        com.polyauth.oop.domain.model.RoleExclusion exclusion =
                roleExclusionRepository.findPair(roleId, excludedRoleId)
                        .orElseThrow(() -> new UseCaseException(
                                ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                "角色互斥关系不存在"));
        roleExclusionRepository.delete(exclusion.id());
        auditTrail.recordPermissionChange(operatorId, ADMIN_SYSTEM_CODE,
                "UNBIND_ROLE_EXCLUSION",
                "role:" + exclusion.roleId()
                        + "/exclude:" + exclusion.excludedRoleId(),
                Map.of("roleId", exclusion.roleId(),
                        "excludedRoleId", exclusion.excludedRoleId()), null);
    }

    private Role requireRole(Long roleId) {
        if (roleId == null) {
            throw new UseCaseException(ApplicationErrorCode.PARAM_INVALID, "角色 ID 不能为空");
        }
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + roleId));
    }

    private void ensureNoExclusion(Long userId, Long roleId) {
        Set<Long> activeRoleIds = roleGrantRepository.findByUserId(userId).stream()
                .filter(grant -> grant.status().name().equals("ACTIVE"))
                .filter(grant -> grant.isEffective(clock.instant()))
                .map(RoleGrant::roleId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        boolean conflict = roleExclusionRepository.findByRole(roleId).stream()
                .map(item -> item.peerOf(roleId))
                .anyMatch(activeRoleIds::contains);
        if (conflict) {
            throw new UseCaseException(
                    ApplicationErrorCode.ROLE_EXCLUSION_CONFLICT,
                    "角色与已授予角色互斥");
        }
    }

    private void checkGrantBoundary(Long operatorId, Long roleId) {
        Set<Long> rolePermissions = inheritedPermissionIds(roleId);
        if (operatorId == null || rolePermissions.isEmpty()) {
            return;
        }
        Set<Long> operatorPermissions =
                permissionAccessUseCase.permissions(operatorId, ADMIN_SYSTEM_CODE);
        if (!operatorPermissions.containsAll(rolePermissions)) {
            throw new UseCaseException(
                    ApplicationErrorCode.NO_FUNCTION_PERM,
                    "不能授予超出自身权限的角色: " + roleId);
        }
    }

    private Set<Long> inheritedPermissionIds(Long roleId) {
        Set<Long> roleIds = new HashSet<>();
        Set<Long> pending = new HashSet<>();
        pending.add(roleId);
        while (!pending.isEmpty()) {
            Set<Long> next = new HashSet<>();
            for (Role role : roleRepository.findAll()) {
                if (!pending.contains(role.id()) || !role.isActive()) {
                    continue;
                }
                if (roleIds.add(role.id()) && role.parentId() != null) {
                    next.add(role.parentId());
                }
            }
            pending = next;
        }
        Set<Long> permissions = new HashSet<>();
        roleIds.forEach(id -> rolePermissionRepository.findByRoleId(id).stream()
                .map(RolePermissionGrant::permissionId)
                .forEach(permissions::add));
        return permissions;
    }

    private void validateParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        Role parent = roleRepository.findById(parentId)
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND, "父角色不存在: " + parentId));
        if (!parent.isActive()) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "父角色已停用: " + parentId);
        }
    }

    private RoleType parseRoleType(String value) {
        try {
            return RoleType.valueOf(requireText(value, "角色类型不能为空"));
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "角色类型必须为 FUNC");
        }
    }

    private RoleStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RoleStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "角色状态必须为 ACTIVE 或 DISABLED");
        }
    }

    private OperationAction parseOperationAction(String value) {
        try {
            return OperationAction.valueOf(requireText(value, "授权操作不能为空"));
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "未知权限操作: " + value);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new UseCaseException(ApplicationErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
