package com.polyauth.structured.application.decision;

import com.polyauth.structured.common.enums.AccountStatus;
import com.polyauth.structured.common.enums.OperationAction;
import com.polyauth.structured.common.enums.RoleType;
import com.polyauth.structured.infrastructure.persistence.entity.IamRole;
import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import com.polyauth.structured.infrastructure.persistence.repository.IamRolePermissionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 策略决策点（PDP）应用服务（M08）。
 *
 * <p>统一鉴权入口：只做功能权限判定。
 * 被网关与业务系统通过 OpenAPI 调用。
 * 对应功能权限查询与判定。
 *
 * <p>RBAC 判定链：用户正常 → 有效用户-角色绑定（未回收、在有效期内）→ 有效角色（含父角色继承）
 * → 角色-前端权限绑定（allowed_action 覆盖权限声明的操作）→ 权限属于该子系统且均启用。
 * 角色→权限的候选由单条 join 查询一次性取得，避免多轮查库。
 */
@RequiredArgsConstructor
@Service
public class DecisionService {

    private final IamUserRepository userRepository;
    private final IamUserRoleRepository userRoleRepository;
    private final IamRoleRepository roleRepository;
    private final IamRolePermissionRepository rolePermissionRepository;

    /**
     * 前端权限判定。
     *
     * @param userId     用户 ID
     * @param systemCode 子系统编码
     * @param permissionId 前端菜单/按钮权限 ID
     * @return 允许返回 true
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String systemCode, Long permissionId) {
        return getUserPermissions(userId, systemCode).contains(permissionId);
    }

    /**
     * 查询用户在某子系统下已授权的前端权限 ID 集合。
     */
    @Transactional(readOnly = true)
    public Set<Long> getUserPermissions(Long userId, String systemCode) {
        // 1. 用户必须存在且为正常状态
        boolean userNormal = userRepository.findById(userId)
                .filter(u -> u.getIsDeleted() == 0 && AccountStatus.NORMAL.getCode().equals(u.getAccountStatus()))
                .isPresent();
        if (!userNormal) {
            return Set.of();
        }

        // 2. 有效角色（含父角色继承）
        Set<Long> roleIds = activeRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        // 3. 单条 join 查询取候选权限（已按系统/权限启用过滤），并合并 allowed_action 覆盖判定
        Set<Long> granted = new HashSet<>();
        for (IamRolePermissionRepository.GrantedPermissionView view
                : rolePermissionRepository.findGrantedPermissions(roleIds, systemCode)) {
            OperationAction allowed = parseAction(view.getAllowedAction());
            if (allowed == null || allowed == OperationAction.NONE) {
                continue;
            }
            OperationAction required = parseAction(view.getRequiredAction());
            if (required != null && allowed.covers(required)) {
                granted.add(view.getPermissionId());
            }
        }
        return granted;
    }

    /**
     * 查询用户当前生效的角色编码，包含父角色继承并过滤过期或停用授权。
     */
    @Transactional(readOnly = true)
    public Set<String> getUserRoleCodes(Long userId) {
        boolean userNormal = userRepository.findById(userId)
                .filter(u -> u.getIsDeleted() == 0 && AccountStatus.NORMAL.getCode().equals(u.getAccountStatus()))
                .isPresent();
        if (!userNormal) {
            return Set.of();
        }
        return roleRepository.findAllById(activeRoleIds(userId)).stream()
                .map(IamRole::getRoleCode)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * 用户的有效角色集合（含父角色继承，visited 去重以防御继承成环）。
     */
    private Set<Long> activeRoleIds(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> directRoleIds = userRoleRepository
                .findByUserIdAndStatus(userId, IamUserRole.STATUS_ACTIVE).stream()
                .filter(ur -> isEffective(ur, now))
                .map(IamUserRole::getRoleId)
                .collect(Collectors.toSet());
        return collectActiveRolesWithAncestors(directRoleIds);
    }

    private Set<Long> collectActiveRolesWithAncestors(Set<Long> directRoleIds) {
        Set<Long> result = new HashSet<>();
        Set<Long> toVisit = new HashSet<>(directRoleIds);
        while (!toVisit.isEmpty()) {
            List<IamRole> roles = roleRepository.findAllById(toVisit);
            Set<Long> next = new HashSet<>();
            for (IamRole role : roles) {
                if (role.getIsDeleted() != 0
                        || (!RoleType.FUNC.getCode().equals(role.getRoleType())
                        && !RoleType.DATA.getCode().equals(role.getRoleType()))
                        || !IamRole.STATUS_ACTIVE.equals(role.getStatus())) {
                    continue;
                }
                if (!result.add(role.getId())) {
                    continue;
                }
                if (role.getParentId() != null) {
                    next.add(role.getParentId());
                }
            }
            toVisit = next;
        }
        return result;
    }

    private OperationAction parseAction(String code) {
        try {
            return OperationAction.fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isEffective(IamUserRole grant, LocalDateTime now) {
        boolean started = grant.getEffectiveTime() == null || !grant.getEffectiveTime().isAfter(now);
        boolean notExpired = grant.getExpireTime() == null || grant.getExpireTime().isAfter(now);
        return started && notExpired;
    }

}
