package com.polyauth.service.ddd.domain.service;

import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleStatus;
import com.polyauth.service.ddd.domain.model.RoleType;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleHierarchyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 20, 0);

    @Test
    void expandsActiveAncestorsAndStopsAtDisabledRole() {
        Role child = role(3L, 2L, RoleStatus.ACTIVE);
        Role parent = role(2L, 1L, RoleStatus.ACTIVE);
        Role disabledGrandparent = role(1L, null, RoleStatus.DISABLED);
        Map<Long, Role> roles = Map.of(1L, disabledGrandparent, 2L, parent, 3L, child);

        Set<Long> result = RoleHierarchy.activeRoleIds(
                Set.of(3L), id -> Optional.ofNullable(roles.get(id)));

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("多继承：展开全部父角色分支")
    void expandsAllParentBranches() {
        Role rootA = role(1L, null, RoleStatus.ACTIVE);
        Role rootB = role(2L, null, RoleStatus.ACTIVE);
        Role parentA = role(3L, 1L, RoleStatus.ACTIVE);
        Role parentB = role(4L, 2L, RoleStatus.ACTIVE);
        Role child = Role.restore(5L, "R5", "角色5", RoleType.FUNC,
                java.util.List.of(3L, 4L), RoleStatus.ACTIVE, null, NOW, NOW, 0L, 1L, 1L);
        Map<Long, Role> roles = Map.of(
                1L, rootA, 2L, rootB, 3L, parentA, 4L, parentB, 5L, child);

        Set<Long> result = RoleHierarchy.activeRoleIds(
                Set.of(5L), id -> Optional.ofNullable(roles.get(id)));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void cycleIsTraversedOnlyOnce() {
        Role first = role(1L, 2L, RoleStatus.ACTIVE);
        Role second = role(2L, 1L, RoleStatus.ACTIVE);
        Map<Long, Role> roles = Map.of(1L, first, 2L, second);

        Set<Long> result = RoleHierarchy.activeRoleIds(
                Set.of(1L), id -> Optional.ofNullable(roles.get(id)));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void changingParentRejectsCycle() {
        Role child = role(2L, null, RoleStatus.ACTIVE);
        Role candidateParent = role(3L, 2L, RoleStatus.ACTIVE);
        Map<Long, Role> roles = Map.of(2L, child, 3L, candidateParent);

        assertThatThrownBy(() -> RoleHierarchy.ensureNoCycle(
                2L, 3L, id -> Optional.ofNullable(roles.get(id))))
                .isInstanceOf(DomainRuleException.class);
    }

    private static Role role(Long id, Long parentId, RoleStatus status) {
        return Role.restore(id, "R" + id, "角色" + id, RoleType.FUNC,
                parentId, status, null, NOW, NOW, 0L, 1L, 1L);
    }
}
