package com.polyauth.structured.application.datascope;

import com.polyauth.structured.application.api.ApiService;
import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.infrastructure.persistence.entity.IamApi;
import com.polyauth.structured.infrastructure.persistence.entity.IamOrg;
import com.polyauth.structured.infrastructure.persistence.entity.IamRole;
import com.polyauth.structured.infrastructure.persistence.entity.IamRoleDataScope;
import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import com.polyauth.structured.infrastructure.persistence.repository.IamOrgRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 功能权限与数据权限联合判定服务。
 */
@Service
@RequiredArgsConstructor
public class DataPermissionService {

    private final DecisionService decisionService;
    private final ApiService apiService;
    private final IamRoleDataScopeRepository roleDataScopeRepository;
    private final IamRoleRepository roleRepository;
    private final IamOrgRepository orgRepository;
    private final IamUserRoleRepository userRoleRepository;

    /**
     * 判定用户是否可对目标资源执行指定 API。
     */
    public ResourceDecision authorize(Long userId, String systemCode, Long apiId,
                                      Long targetOrgId, Long targetProjectId, Long targetUserId) {
        IamApi api = apiService.getApi(apiId);
        boolean functionAllowed = api.getPermissionId() != null
                && decisionService.hasPermission(userId, systemCode, api.getPermissionId());
        if (!functionAllowed) {
            return ResourceDecision.builder()
                    .functionAllowed(false)
                    .dataAllowed(false)
                    .allowed(false)
                    .matchedScopeIds(List.of())
                    .build();
        }

        Set<Long> roleIds = activeRoleIds(userId);
        if (roleIds.isEmpty()) {
            return deniedFunctionPassed();
        }
        List<IamRoleDataScope> scopes = roleDataScopeRepository.findByRoleIdIn(roleIds);
        List<Long> matched = scopes.stream()
                .filter(scope -> isEffective(scope, LocalDateTime.now()))
                .filter(scope -> matches(scope, userId, targetOrgId, targetProjectId, targetUserId))
                .map(IamRoleDataScope::getId)
                .toList();
        return ResourceDecision.builder()
                .functionAllowed(true)
                .dataAllowed(!matched.isEmpty())
                .allowed(!matched.isEmpty())
                .matchedScopeIds(matched)
                .build();
    }

    /**
     * 获取用户有效直接角色及全部父角色。
     */
    private Set<Long> activeRoleIds(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> directRoleIds = userRoleRepository
                .findByUserIdAndStatus(userId, IamUserRole.STATUS_ACTIVE).stream()
                .filter(grant -> isEffective(grant, now))
                .map(IamUserRole::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> result = new HashSet<>();
        Set<Long> toVisit = new HashSet<>(directRoleIds);
        while (!toVisit.isEmpty()) {
            List<IamRole> roles = roleRepository.findAllById(toVisit);
            Set<Long> next = new HashSet<>();
            for (IamRole role : roles) {
                if (role.getIsDeleted() != 0 || !IamRole.STATUS_ACTIVE.equals(role.getStatus())) {
                    continue;
                }
                if (result.add(role.getId()) && role.getParentId() != null) {
                    next.add(role.getParentId());
                }
            }
            toVisit = next;
        }
        return result;
    }

    /**
     * 判断单个角色数据范围是否覆盖目标资源。
     */
    private boolean matches(IamRoleDataScope scope, Long userId, Long targetOrgId,
                            Long targetProjectId, Long targetUserId) {
        return switch (scope.getScopeType()) {
            case IamRoleDataScope.TYPE_ALL -> true;
            case IamRoleDataScope.TYPE_SELF -> targetUserId != null && targetUserId.equals(userId);
            case IamRoleDataScope.TYPE_PROJECT -> targetProjectId != null
                    && targetProjectId.equals(scope.getProjectId());
            case IamRoleDataScope.TYPE_BRANCH, IamRoleDataScope.TYPE_DEPT ->
                    targetOrgId != null && orgContains(scope.getOrgId(), targetOrgId);
            default -> false;
        };
    }

    /**
     * 判断目标组织是否位于授权组织子树内。
     */
    private boolean orgContains(Long scopeOrgId, Long targetOrgId) {
        if (scopeOrgId == null) {
            return false;
        }
        if (scopeOrgId.equals(targetOrgId)) {
            return true;
        }
        IamOrg target = orgRepository.findById(targetOrgId)
                .filter(item -> item.getIsDeleted() == 0
                        && IamOrg.STATUS_ACTIVE.equals(item.getStatus()))
                .orElse(null);
        return target != null
                && ("," + target.getAncestors() + ",").contains("," + scopeOrgId + ",");
    }

    /**
     * 判断用户角色授权当前是否在有效期内。
     */
    private boolean isEffective(IamUserRole grant, LocalDateTime now) {
        boolean started = grant.getEffectiveTime() == null || !grant.getEffectiveTime().isAfter(now);
        boolean notExpired = grant.getExpireTime() == null || grant.getExpireTime().isAfter(now);
        return started && notExpired;
    }

    /**
     * 判断角色数据范围授权当前是否在有效期内。
     */
    private boolean isEffective(IamRoleDataScope scope, LocalDateTime now) {
        boolean active = IamRoleDataScope.STATUS_ACTIVE.equals(scope.getStatus());
        boolean started = scope.getEffectiveTime() == null || !scope.getEffectiveTime().isAfter(now);
        boolean notExpired = scope.getExpireTime() == null || scope.getExpireTime().isAfter(now);
        return active && started && notExpired;
    }

    /**
     * 查询用户当前生效的数据范围授权，包含父角色继承得到的数据范围。
     */
    @Transactional(readOnly = true)
    public List<EffectiveDataScope> getEffectiveDataScopes(Long userId, String systemCode) {
        Set<Long> roleIds = activeRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return roleDataScopeRepository.findByRoleIdIn(roleIds).stream()
                .filter(scope -> isEffective(scope, now))
                .map(scope -> EffectiveDataScope.builder()
                        .bindingId(scope.getId())
                        .roleId(scope.getRoleId())
                        .scopeType(scope.getScopeType())
                        .orgId(scope.getOrgId())
                        .projectId(scope.getProjectId())
                        .expireTime(scope.getExpireTime())
                        .build())
                .toList();
    }

    /**
     * 功能权限通过但无数据范围时的固定拒绝结果。
     */
    private ResourceDecision deniedFunctionPassed() {
        return ResourceDecision.builder()
                .functionAllowed(true)
                .dataAllowed(false)
                .allowed(false)
                .matchedScopeIds(List.of())
                .build();
    }
}
