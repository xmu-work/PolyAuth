package com.polyauth.service.ddd.domain.service;

import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.exception.DomainErrorCode;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 计算角色多继承链上的有效角色集合。
 */
public final class RoleHierarchy {

    private RoleHierarchy() {
    }

    /**
     * 展开直接角色及其启用祖先（支持多个父角色），循环继承只访问一次。
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
            pending.addAll(role.parentIds());
        }
        return Set.copyOf(active);
    }

    /**
     * 校验把一组父角色绑定给当前角色是否会形成继承环。
     */
    public static void ensureNoCycle(Long roleId, Collection<Long> candidateParentIds,
                                     Function<Long, Optional<Role>> roleLookup) {
        if (candidateParentIds == null || candidateParentIds.isEmpty()) {
            return;
        }
        if (candidateParentIds.contains(roleId)) {
            throw new DomainRuleException(
                    DomainErrorCode.ROLE_INHERIT_CYCLE, "角色不能继承自身");
        }
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>(candidateParentIds);
        while (!pending.isEmpty()) {
            Long cursor = pending.removeFirst();
            if (cursor == null || !visited.add(cursor)) {
                continue;
            }
            if (cursor.equals(roleId)) {
                throw new DomainRuleException(
                        DomainErrorCode.ROLE_INHERIT_CYCLE, "角色继承关系不能成环");
            }
            pending.addAll(List.copyOf(roleLookup.apply(cursor)
                    .map(Role::parentIds)
                    .orElse(List.of())));
        }
    }

    /**
     * 校验单个父角色（兼容旧调用）。
     */
    public static void ensureNoCycle(Long roleId, Long candidateParentId,
                                     Function<Long, Optional<Role>> roleLookup) {
        ensureNoCycle(roleId,
                candidateParentId == null ? List.of() : List.of(candidateParentId),
                roleLookup);
    }
}
