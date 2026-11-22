package com.polyauth.oop.domain.service;

import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleStatus;
import com.polyauth.oop.domain.model.RoleType;
import com.polyauth.oop.domain.exception.DomainRuleException;
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
