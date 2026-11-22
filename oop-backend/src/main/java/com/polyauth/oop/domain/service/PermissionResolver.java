package com.polyauth.oop.domain.service;

import com.polyauth.oop.domain.model.OperationAction;
import com.polyauth.oop.domain.model.PermissionFact;
import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RolePermissionGrant;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 根据已读取的授权事实纯计算用户功能权限集合。
 */
public final class PermissionResolver {

    private PermissionResolver() {
    }

    /**
     * 过滤有效授权、展开角色继承、匹配系统与操作后去重聚合。
     */
    public static Set<Long> resolve(List<RoleGrant> grants, Map<Long, Role> roles,
                                    List<RolePermissionGrant> permissionGrants,
                                    List<PermissionFact> permissionFacts,
                                    String systemCode, Instant now) {
        Set<Long> directRoleIds = grants.stream()
                .filter(grant -> grant.isEffective(now))
                .map(RoleGrant::roleId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> effectiveRoleIds = RoleHierarchy.activeRoleIds(
                directRoleIds, id -> java.util.Optional.ofNullable(roles.get(id)));
        if (effectiveRoleIds.isEmpty() || systemCode == null) {
            return Set.of();
        }

        Map<Long, PermissionFact> factsById = new HashMap<>();
        for (PermissionFact fact : permissionFacts) {
            if (fact.active() && systemCode.equals(fact.systemCode())) {
                factsById.put(fact.permissionId(), fact);
            }
        }

        Set<Long> result = new TreeSet<>();
        for (RolePermissionGrant binding : permissionGrants) {
            if (!effectiveRoleIds.contains(binding.roleId())
                    || binding.allowedAction() == null
                    || binding.allowedAction() == OperationAction.NONE) {
                continue;
            }
            PermissionFact fact = factsById.get(binding.permissionId());
            if (fact != null && binding.allowedAction().covers(fact.requiredAction())) {
                result.add(fact.permissionId());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
