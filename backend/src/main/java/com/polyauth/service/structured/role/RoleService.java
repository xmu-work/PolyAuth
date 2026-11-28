package com.polyauth.service.structured.role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.polyauth.service.port.role.RoleUseCase;

import com.polyauth.service.structured.auth.AuthService;
import com.polyauth.service.structured.decision.DecisionService;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.service.common.enums.AccountStatus;
import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.enums.OperationAction;
import com.polyauth.service.common.enums.RoleType;
import com.polyauth.service.common.exception.BusinessException;
import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import com.polyauth.infrastructure.persistence.structured.entity.IamRoleExclusion;
import com.polyauth.infrastructure.persistence.structured.entity.IamRoleParent;
import com.polyauth.infrastructure.persistence.structured.entity.IamRolePermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamUser;
import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;
import com.polyauth.infrastructure.persistence.structured.repository.IamPermissionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRolePermissionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleExclusionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleParentRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRoleRepository;
import com.polyauth.api.shared.RequestIdFilter;
import com.polyauth.infrastructure.cache.BusinessLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色能力服务。
 *
 * <p>角色的整个生命周期集中在这里：角色定义、用户-角色授权、角色-前端权限授权。
 * 不再另设 GrantService，避免同一角色能力散落在多个服务中。
 */
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured", matchIfMissing = true)
public class RoleService implements RoleUseCase {

    public static final String ROLE_ACTIVE = IamRole.STATUS_ACTIVE;
    public static final String GRANT_ACTIVE = IamUserRole.STATUS_ACTIVE;
    public static final String GRANT_REVOKED = IamUserRole.STATUS_REVOKED;
    /** 所有角色内置的数据查看权限。 */
    public static final long DATA_VIEW_PERMISSION_ID = 10009L;
    public static final String DATA_VIEW_PERMISSION_CODE = "DATA_VIEW";

    private static final int MAX_BATCH_PERMISSIONS = 200;

    private final IamRoleRepository roleRepository;
    private final IamUserRepository userRepository;
    private final IamUserRoleRepository userRoleRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamRolePermissionRepository rolePermissionRepository;
    private final IamRoleParentRepository roleParentRepository;
    private final IamRoleExclusionRepository roleExclusionRepository;
    private final DecisionService decisionService;
    private final LogService logService;
    private final BusinessLockExecutor lockExecutor;

    @Transactional
    public IamRole createRole(String roleCode, String roleName, String roleType, String description) {
        return createRole(roleCode, roleName, roleType, description, List.of());
    }

    /**
     * 创建角色并指定多个父角色。
     */
    @Transactional
    public IamRole createRole(String roleCode, String roleName, String roleType, String description,
                              List<Long> parentIds) {
        RoleType.fromCode(roleType);
        String effectiveCode = normalizeRoleCode(roleCode);
        if (roleRepository.findByRoleCode(effectiveCode).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "角色编码已存在: " + effectiveCode);
        }
        List<Long> normalizedParents = normalizeParentIds(parentIds);
        validateParentRoles(normalizedParents);

        LocalDateTime now = LocalDateTime.now();
        IamRole role = new IamRole();
        role.setRoleCode(effectiveCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        // parent_id 保留为首个父角色，用于兼容旧读取方；完整继承关系在 iam_role_parent。
        role.setParentId(normalizedParents.isEmpty() ? null : normalizedParents.get(0));
        role.setDescription(description);
        role.setStatus(ROLE_ACTIVE);
        role.setIsDeleted(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        IamRole saved = roleRepository.save(role);
        replaceRoleParents(saved.getId(), normalizedParents, now);
        ensureBuiltinDataViewPermission(saved.getId(), now);
        return saved;
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode != null && !roleCode.isBlank()) {
            return roleCode.trim();
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String generated = "ROLE_"
                    + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            if (roleRepository.findByRoleCode(generated).isEmpty()) {
                return generated;
            }
        }
        throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "角色编码生成失败，请重试");
    }

    /**
     * 创建角色并指定单个父角色（兼容旧调用）。
     */
    @Transactional
    public IamRole createRole(String roleCode, String roleName, String roleType, String description,
                              Long parentId) {
        return createRole(roleCode, roleName, roleType, description,
                parentId == null ? List.of() : List.of(parentId));
    }

    /**
     * 修改角色基础信息和多个父角色。
     */
    @Transactional
    public IamRole updateRole(Long roleId, String roleName, String description, String status,
                              List<Long> parentIds, Long operatorId) {
        return lockExecutor.execute("role:update:" + roleId,
                () -> updateRoleLocked(roleId, roleName, description, status, parentIds, operatorId));
    }

    /**
     * 修改角色并指定单个父角色（兼容旧调用）。
     */
    @Transactional
    public IamRole updateRole(Long roleId, String roleName, String description, String status,
                              Long parentId, Long operatorId) {
        return updateRole(roleId, roleName, description, status,
                parentId == null ? List.of() : List.of(parentId), operatorId);
    }

    /**
     * 在角色级锁内完成角色更新。
     */
    private IamRole updateRoleLocked(Long roleId, String roleName, String description, String status,
                                     List<Long> parentIds, Long operatorId) {
        IamRole role = getRole(roleId);
        List<Long> normalizedParents = normalizeParentIds(parentIds);
        if (normalizedParents.contains(roleId)) {
            throw new BusinessException(ErrorCode.ROLE_INHERIT_CYCLE, "角色不能继承自身");
        }
        validateParentRoles(normalizedParents);
        if (wouldCreateCycle(roleId, normalizedParents)) {
            throw new BusinessException(ErrorCode.ROLE_INHERIT_CYCLE, "角色继承关系不能成环");
        }
        if (status != null && !ROLE_ACTIVE.equals(status) && !IamRole.STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色状态必须为 ACTIVE 或 DISABLED");
        }

        Map<String, Object> before = roleSnapshot(role);
        if (roleName != null && !roleName.isBlank()) {
            role.setRoleName(roleName);
        }
        role.setDescription(description);
        role.setParentId(normalizedParents.isEmpty() ? null : normalizedParents.get(0));
        if (status != null) {
            role.setStatus(status);
        }
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        IamRole saved = roleRepository.save(role);
        LocalDateTime now = svSavedAt(saved);
        replaceRoleParents(roleId, normalizedParents, now);
        ensureBuiltinDataViewPermission(roleId, now);
        recordPermissionChange(operatorId, "UPDATE_ROLE", "role:" + roleId,
                before, roleSnapshot(saved));
        return saved;
    }

    /**
     * 所有角色内置数据查看权限；权限节点尚未迁移时静默跳过。
     */
    private void ensureBuiltinDataViewPermission(Long roleId, LocalDateTime now) {
        if (roleId == null
                || rolePermissionRepository.findByRoleIdAndPermissionId(
                        roleId, DATA_VIEW_PERMISSION_ID).isPresent()) {
            return;
        }
        boolean permissionExists = permissionRepository.findById(DATA_VIEW_PERMISSION_ID)
                .filter(permission -> permission.getIsDeleted() == 0)
                .isPresent();
        if (!permissionExists) {
            return;
        }
        IamRolePermission binding = new IamRolePermission();
        binding.setRoleId(roleId);
        binding.setPermissionId(DATA_VIEW_PERMISSION_ID);
        binding.setAllowedAction(OperationAction.VIEW.getCode());
        binding.setCreatedAt(now);
        rolePermissionRepository.save(binding);
    }

    private LocalDateTime svSavedAt(IamRole role) {
        return role.getUpdatedAt() == null ? LocalDateTime.now() : role.getUpdatedAt();
    }

    /**
     * 新建一对角色互斥关系。
     */
    @Transactional
    public void bindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId) {
        long firstId = Math.min(roleId, excludedRoleId);
        long secondId = Math.max(roleId, excludedRoleId);
        lockExecutor.execute("role-exclusion:" + firstId + ":" + secondId,
                () -> bindRoleExclusionLocked(roleId, excludedRoleId, operatorId));
    }

    /**
     * 在角色互斥唯一键锁内保存互斥关系。
     */
    private Void bindRoleExclusionLocked(Long roleId, Long excludedRoleId, Long operatorId) {
        if (roleId == null || excludedRoleId == null || roleId.equals(excludedRoleId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "互斥角色不能相同");
        }
        getRole(roleId);
        getRole(excludedRoleId);
        if (!userRoleRepository.findUserIdsWithBothActiveRoles(roleId, excludedRoleId).isEmpty()) {
            throw new BusinessException(ErrorCode.ROLE_EXCLUSION_CONFLICT,
                    "已有用户同时拥有这两个角色，请先回收冲突授权");
        }
        Long first = Math.min(roleId, excludedRoleId);
        Long second = Math.max(roleId, excludedRoleId);
        if (roleExclusionRepository.findByRoleIdAndExcludedRoleId(first, second).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "角色互斥关系已存在");
        }
        IamRoleExclusion exclusion = new IamRoleExclusion();
        exclusion.setRoleId(first);
        exclusion.setExcludedRoleId(second);
        exclusion.setCreatedBy(operatorId);
        exclusion.setCreatedAt(LocalDateTime.now());
        roleExclusionRepository.save(exclusion);
        recordPermissionChange(operatorId, "BIND_ROLE_EXCLUSION", "role:" + first + "/exclude:" + second,
                null, Map.of("roleId", first, "excludedRoleId", second));
        return null;
    }

    /**
     * 删除一对角色互斥关系。
     */
    @Transactional
    public void unbindRoleExclusion(Long roleId, Long excludedRoleId, Long operatorId) {
        Long first = Math.min(roleId, excludedRoleId);
        Long second = Math.max(roleId, excludedRoleId);
        IamRoleExclusion exclusion = roleExclusionRepository
                .findByRoleIdAndExcludedRoleId(first, second)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "角色互斥关系不存在"));
        roleExclusionRepository.delete(exclusion);
        recordPermissionChange(operatorId, "UNBIND_ROLE_EXCLUSION", "role:" + first + "/exclude:" + second,
                Map.of("roleId", first, "excludedRoleId", second), null);
    }

    /**
     * 查询角色的全部互斥关系。
     */
    public List<Long> listRoleExclusions(Long roleId) {
        getRole(roleId);
        return roleExclusionRepository.findByRoleIdOrExcludedRoleId(roleId, roleId).stream()
                .map(item -> roleId.equals(item.getRoleId()) ? item.getExcludedRoleId() : item.getRoleId())
                .distinct()
                .sorted()
                .toList();
    }

    public IamRole getRole(Long roleId) {
        return roleRepository.findById(roleId)
                .filter(this::isFunctionRole)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + roleId));
    }

    public List<IamRole> listRoles() {
        return roleRepository.findAll().stream().filter(this::isFunctionRole).toList();
    }

    /**
     * 为用户授予角色。一个用户在同一时刻可以拥有多个角色。
     */
    @Transactional
    public IamUserRole grantRole(Long userId, Long roleId, LocalDateTime expireTime,
                                 Long grantBy, String grantReason) {
        return lockExecutor.execute("role:grant:" + userId + ":" + roleId,
                () -> grantRoleLocked(userId, roleId, expireTime, grantBy, grantReason));
    }

    /**
     * 在用户与角色唯一键锁内完成授予或恢复。
     */
    private IamUserRole grantRoleLocked(Long userId, Long roleId, LocalDateTime expireTime,
                                        Long grantBy, String grantReason) {
        IamUser user = userRepository.findById(userId)
                .filter(u -> u.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + userId));
        if (!AccountStatus.NORMAL.getCode().equals(user.getAccountStatus())) {
            throw new BusinessException(ErrorCode.TARGET_USER_ABNORMAL, "用户非正常状态，不能授权: " + userId);
        }

        IamRole role = getRole(roleId);
        if (!ROLE_ACTIVE.equals(role.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色已停用: " + roleId);
        }

        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && !expireTime.isAfter(now)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "失效时间必须晚于当前时间");
        }

        IamUserRole grant = userRoleRepository.findByUserIdAndRoleId(userId, roleId).orElse(null);
        if (grant != null && GRANT_ACTIVE.equals(grant.getStatus())) {
            throw new BusinessException(ErrorCode.GRANT_DUPLICATE);
        }
        checkRoleExclusion(userId, roleId);
        checkGrantBoundary(grantBy, roleId);
        Map<String, Object> before = grant == null ? null : grantSnapshot(grant);
        if (grant == null) {
            grant = new IamUserRole();
            grant.setUserId(userId);
            grant.setRoleId(roleId);
            grant.setCreatedAt(now);
        }
        grant.setEffectiveTime(now);
        grant.setExpireTime(expireTime);
        grant.setGrantBy(grantBy);
        grant.setGrantReason(grantReason);
        grant.setStatus(GRANT_ACTIVE);
        grant.setUpdatedAt(now);
        IamUserRole saved = userRoleRepository.save(grant);
        recordPermissionChange(grantBy, "GRANT_ROLE", "user:" + userId + "/role:" + roleId,
                before, grantSnapshot(saved));
        return saved;
    }

    @Transactional
    public void grantRoles(Long userId, List<Long> roleIds, LocalDateTime expireTime,
                           Long grantBy, String grantReason) {
        for (Long roleId : roleIds) {
            grantRole(userId, roleId, expireTime, grantBy, grantReason);
        }
    }

    @Transactional
    public void revokeRole(Long userId, Long roleId, Long operatorId) {
        IamUserRole grant = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .filter(g -> GRANT_ACTIVE.equals(g.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户未被授予该角色"));
        revokeGrant(grant, operatorId);
    }

    @Transactional
    public void revokeAllRoles(Long userId) {
        revokeAllRoles(userId, null);
    }

    @Transactional
    public void revokeAllRoles(Long userId, Long operatorId) {
        userRoleRepository.findByUserIdAndStatus(userId, GRANT_ACTIVE)
                .forEach(grant -> revokeGrant(grant, operatorId));
    }

    public List<IamUserRole> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }

    public List<IamUserRole> getRoleUsers(Long roleId) {
        return userRoleRepository.findByRoleId(roleId);
    }

    /**
     * 为角色授予前端菜单/按钮权限。
     */
    @Transactional
    public void bindRolePermissions(Long roleId, List<Long> permissionIds,
                                    String allowedAction, Long createdBy) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "权限列表不能为空");
        }
        if (permissionIds.size() > MAX_BATCH_PERMISSIONS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "单次授权权限数量不能超过 " + MAX_BATCH_PERMISSIONS);
        }
        IamRole role = getRole(roleId);
        if (!ROLE_ACTIVE.equals(role.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色已停用: " + roleId);
        }
        OperationAction.fromCode(allowedAction);

        Set<Long> bound = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(IamRolePermission::getPermissionId)
                .collect(Collectors.toSet());
        Set<Long> before = new HashSet<>(bound);
        LocalDateTime now = LocalDateTime.now();
        for (Long permissionId : permissionIds) {
            IamPermission permission = permissionRepository.findById(permissionId)
                    .filter(p -> p.getIsDeleted() == 0)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "权限不存在: " + permissionId));
            if (!ROLE_ACTIVE.equals(permission.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "权限已停用: " + permissionId);
            }
            if (!bound.add(permissionId)) {
                continue;
            }
            IamRolePermission binding = new IamRolePermission();
            binding.setRoleId(roleId);
            binding.setPermissionId(permissionId);
            binding.setAllowedAction(allowedAction);
            binding.setCreatedAt(now);
            binding.setCreatedBy(createdBy);
            rolePermissionRepository.save(binding);
        }
        Set<Long> after = currentPermissionIds(roleId);
        if (!before.equals(after)) {
            recordPermissionChange(createdBy, "BIND_ROLE_PERMISSION", "role:" + roleId,
                    permissionSnapshot(roleId, before, allowedAction),
                    permissionSnapshot(roleId, after, allowedAction));
        }
    }

    @Transactional
    public void unbindRolePermissions(Long roleId, List<Long> permissionIds, Long operatorId) {
        if (permissionIds != null && permissionIds.contains(DATA_VIEW_PERMISSION_ID)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "数据查看为所有角色内置权限，不能取消");
        }
        Set<Long> before = currentPermissionIds(roleId);
        rolePermissionRepository.findByRoleId(roleId).stream()
                .filter(rp -> permissionIds.contains(rp.getPermissionId()))
                .forEach(rolePermissionRepository::delete);
        Set<Long> after = currentPermissionIds(roleId);
        if (!before.equals(after)) {
            recordPermissionChange(operatorId, "UNBIND_ROLE_PERMISSION", "role:" + roleId,
                    permissionSnapshot(roleId, before, null),
                    permissionSnapshot(roleId, after, null));
        }
    }

    public List<IamRolePermission> getRolePermissions(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId);
    }

    private void revokeGrant(IamUserRole grant, Long operatorId) {
        Map<String, Object> before = grantSnapshot(grant);
        grant.setStatus(GRANT_REVOKED);
        grant.setUpdatedAt(LocalDateTime.now());
        IamUserRole saved = userRoleRepository.save(grant);
        recordPermissionChange(operatorId, "REVOKE_ROLE",
                "user:" + saved.getUserId() + "/role:" + saved.getRoleId(),
                before, grantSnapshot(saved));
    }

    /**
     * 防提权：操作人只能授予权限集合不超出自身的角色。
     */
    private void checkGrantBoundary(Long operatorId, Long roleId) {
        if (operatorId == null) {
            return;
        }
        // 平台超级管理员可以授予任意功能角色（含其他业务系统登记的角色）。
        if (decisionService.getUserRoleCodes(operatorId).contains("super_admin")) {
            return;
        }
        Set<Long> rolePermissions = collectRolePermissionIds(roleId);
        if (rolePermissions.isEmpty()) {
            return;
        }
        Set<Long> operatorPermissions =
                decisionService.getUserPermissions(operatorId, AuthService.ADMIN_SYSTEM_CODE);
        if (!operatorPermissions.containsAll(rolePermissions)) {
            throw new BusinessException(ErrorCode.NO_FUNCTION_PERM, "不能授予超出自身权限的角色: " + roleId);
        }
    }

    private Set<Long> collectRolePermissionIds(Long roleId) {
        Set<Long> roleIds = new HashSet<>();
        Set<Long> toVisit = new HashSet<>();
        toVisit.add(roleId);
        while (!toVisit.isEmpty()) {
            Set<Long> next = new HashSet<>();
            Set<Long> validIds = new HashSet<>();
            for (IamRole role : roleRepository.findAllById(toVisit)) {
                if (role.getIsDeleted() != 0 || !ROLE_ACTIVE.equals(role.getStatus())) {
                    continue;
                }
                if (roleIds.add(role.getId())) {
                    validIds.add(role.getId());
                    if (role.getParentId() != null) {
                        next.add(role.getParentId());
                    }
                }
            }
            if (!validIds.isEmpty()) {
                roleParentRepository.findByRoleIdIn(validIds)
                        .forEach(edge -> next.add(edge.getParentId()));
            }
            toVisit = next;
        }
        Set<Long> permissions = new HashSet<>();
        for (Long id : roleIds) {
            rolePermissionRepository.findByRoleId(id)
                    .forEach(rp -> permissions.add(rp.getPermissionId()));
        }
        return permissions;
    }

    private boolean isFunctionRole(IamRole role) {
        return role.getIsDeleted() == 0
                && RoleType.FUNC.getCode().equals(role.getRoleType());
    }

    private Set<Long> currentPermissionIds(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(IamRolePermission::getPermissionId)
                .collect(Collectors.toSet());
    }

    private Map<String, Object> grantSnapshot(IamUserRole grant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("grantId", grant.getId());
        snapshot.put("userId", grant.getUserId());
        snapshot.put("roleId", grant.getRoleId());
        snapshot.put("effectiveTime", grant.getEffectiveTime());
        snapshot.put("expireTime", grant.getExpireTime());
        snapshot.put("status", grant.getStatus());
        snapshot.put("grantReason", grant.getGrantReason());
        return snapshot;
    }

    private Map<String, Object> permissionSnapshot(Long roleId, Set<Long> permissionIds,
                                                   String allowedAction) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleId", roleId);
        snapshot.put("permissionIds", permissionIds.stream().sorted().toList());
        if (allowedAction != null) {
            snapshot.put("allowedAction", allowedAction);
        }
        return snapshot;
    }

    /**
     * 校验目标用户已拥有的有效角色是否与待授予角色互斥。
     */
    private void checkRoleExclusion(Long userId, Long roleId) {
        Set<Long> activeRoleIds = userRoleRepository.findByUserIdAndStatus(userId, GRANT_ACTIVE).stream()
                .filter(grant -> isGrantEffective(grant, LocalDateTime.now()))
                .map(IamUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (activeRoleIds.isEmpty()) {
            return;
        }
        for (IamRoleExclusion exclusion : roleExclusionRepository
                .findByRoleIdOrExcludedRoleId(roleId, roleId)) {
            Long peerRoleId = roleId.equals(exclusion.getRoleId())
                    ? exclusion.getExcludedRoleId()
                    : exclusion.getRoleId();
            if (activeRoleIds.contains(peerRoleId)) {
                throw new BusinessException(
                        ErrorCode.ROLE_EXCLUSION_CONFLICT,
                        "角色与已授予角色互斥: " + peerRoleId);
            }
        }
    }

    /**
     * 判断用户角色授权是否处于有效期内。
     */
    private boolean isGrantEffective(IamUserRole grant, LocalDateTime now) {
        boolean started = grant.getEffectiveTime() == null || !grant.getEffectiveTime().isAfter(now);
        boolean notExpired = grant.getExpireTime() == null || grant.getExpireTime().isAfter(now);
        return started && notExpired;
    }

    /**
     * 校验父角色存在、启用且未删除。
     */
    private void validateParentRole(Long parentId) {
        IamRole parent = roleRepository.findById(parentId)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "父角色不存在: " + parentId));
        if (!ROLE_ACTIVE.equals(parent.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "父角色已停用: " + parentId);
        }
    }

    /**
     * 沿全部父角色分支检测设置新父角色后是否形成环。
     */
    private boolean wouldCreateCycle(Long roleId, Collection<Long> parentIds) {
        Set<Long> visited = new HashSet<>();
        List<Long> pending = new ArrayList<>(parentIds);
        while (!pending.isEmpty()) {
            Long cursor = pending.remove(pending.size() - 1);
            if (cursor == null || !visited.add(cursor)) {
                continue;
            }
            if (roleId.equals(cursor)) {
                return true;
            }
            pending.addAll(parentIdsOf(cursor));
        }
        return false;
    }

    /**
     * 读取某个角色的全部父角色；优先使用多继承边，兼容尚未迁移的 parent_id。
     */
    private List<Long> parentIdsOf(Long roleId) {
        List<Long> edges = roleParentRepository.findByRoleId(roleId).stream()
                .map(IamRoleParent::getParentId)
                .toList();
        if (!edges.isEmpty()) {
            return edges;
        }
        return roleRepository.findById(roleId)
                .map(IamRole::getParentId)
                .map(List::of)
                .orElse(List.of());
    }

    private List<Long> normalizeParentIds(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        return parentIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private void validateParentRoles(Collection<Long> parentIds) {
        for (Long parentId : parentIds) {
            validateParentRole(parentId);
        }
    }

    private void replaceRoleParents(Long roleId, List<Long> parentIds, LocalDateTime now) {
        roleParentRepository.deleteByRoleId(roleId);
        roleParentRepository.flush();
        if (parentIds.isEmpty()) {
            return;
        }
        List<IamRoleParent> edges = parentIds.stream().map(parentId -> {
            IamRoleParent edge = new IamRoleParent();
            edge.setRoleId(roleId);
            edge.setParentId(parentId);
            edge.setCreatedAt(now);
            return edge;
        }).toList();
        roleParentRepository.saveAll(edges);
    }

    /**
     * 查询角色的全部父角色主键。
     */
    public List<Long> getParentIds(Long roleId) {
        return roleParentRepository.findByRoleId(roleId).stream()
                .map(IamRoleParent::getParentId)
                .sorted()
                .toList();
    }

    /**
     * 批量查询角色的全部父角色主键。
     */
    public Map<Long, List<Long>> getParentIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        roleParentRepository.findByRoleIdIn(roleIds).stream()
                .sorted(java.util.Comparator.comparing(IamRoleParent::getRoleId)
                        .thenComparing(IamRoleParent::getParentId))
                .forEach(edge -> result.computeIfAbsent(edge.getRoleId(), key -> new ArrayList<>())
                        .add(edge.getParentId()));
        return result;
    }

    /**
     * 构造角色基础信息快照。
     */
    private Map<String, Object> roleSnapshot(IamRole role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleId", role.getId());
        snapshot.put("roleCode", role.getRoleCode());
        snapshot.put("roleName", role.getRoleName());
        snapshot.put("roleType", role.getRoleType());
        snapshot.put("parentId", role.getParentId());
        snapshot.put("parentIds", role.getId() == null ? List.of() : getParentIds(role.getId()));
        snapshot.put("status", role.getStatus());
        snapshot.put("description", role.getDescription());
        return snapshot;
    }

    private void recordPermissionChange(Long operatorId, String action, String target,
                                        Object before, Object after) {
        logService.recordPermissionChange(operatorId, AuthService.ADMIN_SYSTEM_CODE,
                action, target, before, after, null, RequestIdFilter.currentRequestId());
    }
}
