package com.polyauth.structured.application.role;

import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.common.enums.AccountStatus;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.enums.OperationAction;
import com.polyauth.structured.common.enums.RoleType;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.persistence.entity.IamPermission;
import com.polyauth.structured.infrastructure.persistence.entity.IamRole;
import com.polyauth.structured.infrastructure.persistence.entity.IamRoleExclusion;
import com.polyauth.structured.infrastructure.persistence.entity.IamRolePermission;
import com.polyauth.structured.infrastructure.persistence.entity.IamUser;
import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import com.polyauth.structured.infrastructure.persistence.repository.IamPermissionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRolePermissionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleExclusionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRoleRepository;
import com.polyauth.structured.interfaces.RequestIdFilter;
import com.polyauth.structured.infrastructure.cache.BusinessLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色能力服务。
 *
 * <p>角色的整个生命周期集中在这里：角色定义、用户-角色授权、角色-前端权限授权。
 * 不再另设 GrantService，避免同一角色能力散落在多个服务中。
 */
@RequiredArgsConstructor
@Service
public class RoleService {

    public static final String ROLE_ACTIVE = IamRole.STATUS_ACTIVE;
    public static final String GRANT_ACTIVE = IamUserRole.STATUS_ACTIVE;
    public static final String GRANT_REVOKED = IamUserRole.STATUS_REVOKED;

    private static final int MAX_BATCH_PERMISSIONS = 200;

    private final IamRoleRepository roleRepository;
    private final IamUserRepository userRepository;
    private final IamUserRoleRepository userRoleRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamRolePermissionRepository rolePermissionRepository;
    private final IamRoleExclusionRepository roleExclusionRepository;
    private final DecisionService decisionService;
    private final LogService logService;
    private final BusinessLockExecutor lockExecutor;

    @Transactional
    public IamRole createRole(String roleCode, String roleName, String roleType, String description) {
        return createRole(roleCode, roleName, roleType, description, null);
    }

    /**
     * 创建角色并指定父角色。
     */
    @Transactional
    public IamRole createRole(String roleCode, String roleName, String roleType, String description,
                              Long parentId) {
        RoleType.fromCode(roleType);
        if (roleRepository.findByRoleCode(roleCode).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATE, "角色编码已存在: " + roleCode);
        }
        if (parentId != null) {
            validateParentRole(parentId);
        }

        LocalDateTime now = LocalDateTime.now();
        IamRole role = new IamRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setParentId(parentId);
        role.setDescription(description);
        role.setStatus(ROLE_ACTIVE);
        role.setIsDeleted(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        return roleRepository.save(role);
    }

    /**
     * 修改角色基础信息和父角色。
     */
    @Transactional
    public IamRole updateRole(Long roleId, String roleName, String description, String status,
                              Long parentId, Long operatorId) {
        return lockExecutor.execute("role:update:" + roleId,
                () -> updateRoleLocked(roleId, roleName, description, status, parentId, operatorId));
    }

    /**
     * 在角色级锁内完成角色更新。
     */
    private IamRole updateRoleLocked(Long roleId, String roleName, String description, String status,
                                     Long parentId, Long operatorId) {
        IamRole role = getRole(roleId);
        if (roleId.equals(parentId)) {
            throw new BusinessException(ErrorCode.ROLE_INHERIT_CYCLE, "角色不能继承自身");
        }
        if (parentId != null) {
            validateParentRole(parentId);
            if (wouldCreateCycle(roleId, parentId)) {
                throw new BusinessException(ErrorCode.ROLE_INHERIT_CYCLE, "角色继承关系不能成环");
            }
        }
        if (status != null && !ROLE_ACTIVE.equals(status) && !IamRole.STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色状态必须为 ACTIVE 或 DISABLED");
        }

        Map<String, Object> before = roleSnapshot(role);
        if (roleName != null && !roleName.isBlank()) {
            role.setRoleName(roleName);
        }
        role.setDescription(description);
        role.setParentId(parentId);
        if (status != null) {
            role.setStatus(status);
        }
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        IamRole saved = roleRepository.save(role);
        recordPermissionChange(operatorId, "UPDATE_ROLE", "role:" + roleId,
                before, roleSnapshot(saved));
        return saved;
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
            for (IamRole role : roleRepository.findAllById(toVisit)) {
                if (role.getIsDeleted() != 0 || !ROLE_ACTIVE.equals(role.getStatus())) {
                    continue;
                }
                if (!roleIds.add(role.getId())) {
                    continue;
                }
                if (role.getParentId() != null) {
                    next.add(role.getParentId());
                }
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
                && (RoleType.FUNC.getCode().equals(role.getRoleType())
                || RoleType.DATA.getCode().equals(role.getRoleType()));
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
     * 沿父角色链检测设置新父角色后是否形成环。
     */
    private boolean wouldCreateCycle(Long roleId, Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (roleId.equals(cursor)) {
                return true;
            }
            cursor = roleRepository.findById(cursor)
                    .map(IamRole::getParentId)
                    .orElse(null);
        }
        return false;
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
