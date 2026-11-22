package com.polyauth.oop.domain.service;

import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 计算角色继承链上的有效角色集合。
 */
public final class RoleHierarchy {

    private RoleHierarchy() {
    }

    /**
     * 展开直接角色及其启用祖先，循环继承只访问一次。
     */
    public static Set<Long> activeRoleIds(Set<Long> directRoleIds,
                                          Function<Long, Optional<Role>> roleLookup) {
        if (directRoleIds == null || directRoleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> visited = new LinkedHashSet<>();
        Set<Long> active = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>(directRoleIds);
        while (!pending.isEmpty()) {
            Long roleId = pending.removeFirst();
            if (roleId == null || !visited.add(roleId)) {
                continue;
            }
            Optional<Role> candidate = roleLookup.apply(roleId);
            if (candidate.isEmpty() || !candidate.get().isActive()) {
                continue;
            }
            Role role = candidate.get();
            active.add(roleId);
            if (role.parentId() != null) {
                pending.addLast(role.parentId());
            }
        }
        return Set.copyOf(active);
    }

    /**
     * 校验把目标父角色绑定给当前角色是否会形成继承环。
     */
    public static void ensureNoCycle(Long roleId, Long candidateParentId,
                                     Function<Long, Optional<Role>> roleLookup) {
        if (candidateParentId == null) {
            return;
        }
        if (candidateParentId.equals(roleId)) {
            throw new DomainRuleException(
                    DomainErrorCode.ROLE_INHERIT_CYCLE, "角色不能继承自身");
        }
        Set<Long> visited = new LinkedHashSet<>();
        Long cursor = candidateParentId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(roleId)) {
                throw new DomainRuleException(
                        DomainErrorCode.ROLE_INHERIT_CYCLE, "角色继承关系不能成环");
            }
            cursor = roleLookup.apply(cursor).map(Role::parentId).orElse(null);
        }
    }
}
