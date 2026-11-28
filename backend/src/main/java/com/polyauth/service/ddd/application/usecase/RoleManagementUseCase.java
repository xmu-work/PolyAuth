package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.command.CreateRoleCommand;
import com.polyauth.service.ddd.application.command.GrantRoleCommand;
import com.polyauth.service.ddd.application.command.UpdateRoleCommand;
import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.application.port.AuditTrail;
import com.polyauth.service.ddd.domain.model.AccountStatus;
import com.polyauth.service.ddd.domain.model.OperationAction;
import com.polyauth.service.ddd.domain.model.PermissionFact;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.RolePermissionGrant;
import com.polyauth.service.ddd.domain.model.RoleStatus;
import com.polyauth.service.ddd.domain.model.RoleType;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.repository.PermissionCatalogRepository;
import com.polyauth.service.ddd.domain.repository.RoleGrantRepository;
import com.polyauth.service.ddd.domain.repository.RolePermissionRepository;
import com.polyauth.service.ddd.domain.repository.RoleRepository;
import com.polyauth.service.ddd.domain.repository.RoleExclusionRepository;
import com.polyauth.service.ddd.domain.repository.UserAccountRepository;
import com.polyauth.service.ddd.domain.service.RoleHierarchy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
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
    /** 所有角色内置的数据查看权限。 */
    public static final long DATA_VIEW_PERMISSION_ID = 10009L;
    private static final Function<Role, Map<String, Object>> ROLE_SNAPSHOT = role -> {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleId", role.id());
        snapshot.put("roleCode", role.roleCode());
        snapshot.put("roleName", role.roleName());
        snapshot.put("roleType", role.roleType().name());
        snapshot.put("parentId", role.parentId());
        snapshot.put("parentIds", role.parentIds());
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
        String roleCode = resolveRoleCode(command.roleCode());
        if (roleRepository.findByCode(roleCode).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "角色编码已存在: " + roleCode);
        }
        RoleType roleType = parseRoleType(command.roleType());
        validateParentRoles(command.parentIds());
        Role role = Role.create(roleCode, command.roleName(), roleType, command.description(),
                command.parentIds(), command.operatorId(), now());
        Role saved = roleRepository.save(role);
        ensureDataViewPermission(saved.id());
        auditTrail.recordPermissionChange(command.operatorId(), ADMIN_SYSTEM_CODE,
                "CREATE_ROLE", "role:" + saved.id(), null, ROLE_SNAPSHOT.apply(saved));
        return saved;
    }

    private String resolveRoleCode(String roleCode) {
        if (roleCode != null && !roleCode.isBlank()) {
            return roleCode.trim();
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String generated = "ROLE_"
                    + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            if (roleRepository.findByCode(generated).isEmpty()) {
                return generated;
            }
        }
        throw new UseCaseException(
                ApplicationErrorCode.RESOURCE_DUPLICATE, "角色编码生成失败，请重试");
    }

    private void ensureDataViewPermission(Long roleId) {
        boolean exists = permissionCatalogRepository.findFact(DATA_VIEW_PERMISSION_ID).isPresent();
        if (!exists) {
            return;
        }
        boolean bound = rolePermissionRepository.findByRoleId(roleId).stream()
                .anyMatch(item -> item.permissionId().equals(DATA_VIEW_PERMISSION_ID));
        if (!bound) {
            rolePermissionRepository.bind(roleId, DATA_VIEW_PERMISSION_ID,
                    OperationAction.VIEW, null);
        }
    }

    /**
     * 更新角色。
     */
    public Role updateRole(UpdateRoleCommand command) {
        Role role = requireRole(command.roleId());
        RoleStatus status = parseStatus(command.status());
        validateParentRoles(command.parentIds());
        Map<String, Object> before = ROLE_SNAPSHOT.apply(role);
        try {
            RoleHierarchy.ensureNoCycle(
                    command.roleId(), command.parentIds(), roleRepository::findById);
            role.update(command.roleName(), command.description(), status,
                    command.parentIds(), command.operatorId(), now());
        } catch (com.polyauth.service.ddd.domain.exception.DomainRuleException exception) {
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
        if (permissionIds != null && permissionIds.contains(DATA_VIEW_PERMISSION_ID)) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "数据查看为所有角色内置权限，不能取消");
        }
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
        com.polyauth.service.ddd.domain.model.RoleExclusion saved =
                roleExclusionRepository.save(
                        com.polyauth.service.ddd.domain.model.RoleExclusion.create(
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
        com.polyauth.service.ddd.domain.model.RoleExclusion exclusion =
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

    private void validateParentRoles(List<Long> parentIds) {
        if (parentIds == null) {
            return;
        }
        for (Long parentId : parentIds) {
            if (parentId == null) {
                continue;
            }
            Role parent = roleRepository.findById(parentId)
                    .orElseThrow(() -> new UseCaseException(
                            ApplicationErrorCode.RESOURCE_NOT_FOUND, "父角色不存在: " + parentId));
            if (!parent.isActive()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID, "父角色已停用: " + parentId);
            }
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
