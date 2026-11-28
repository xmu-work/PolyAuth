package com.polyauth.service.ddd.domain.service;

import com.polyauth.service.ddd.domain.model.OperationAction;
import com.polyauth.service.ddd.domain.model.PermissionFact;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.RolePermissionGrant;
import com.polyauth.service.ddd.domain.model.RoleStatus;
import com.polyauth.service.ddd.domain.model.RoleType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    @Test
    void resolvesInheritedPermissionCoveredByAllowedAction() {
        Role child = role(3L, 2L, RoleStatus.ACTIVE);
        Role parent = role(2L, null, RoleStatus.ACTIVE);
        RoleGrant grant = RoleGrant.grant(1L, 3L, NOW.minusSeconds(60), null, 9L, null, NOW);
        List<RolePermissionGrant> bindings = List.of(
                new RolePermissionGrant(2L, 100L, OperationAction.VIEW_EDIT));
        List<PermissionFact> permissions = List.of(
                new PermissionFact(100L, "COURSE", OperationAction.VIEW, true));

        Set<Long> result = PermissionResolver.resolve(
                List.of(grant), Map.of(2L, parent, 3L, child), bindings, permissions, "COURSE", NOW);

        assertThat(result).containsExactly(100L);
    }

    @Test
    void expiredGrantAndOtherSystemPermissionAreIgnored() {
        Role role = role(2L, null, RoleStatus.ACTIVE);
        RoleGrant expired = RoleGrant.grant(
                1L, 2L, NOW.minusSeconds(120), NOW.minusSeconds(60), 9L, null, NOW.minusSeconds(120));
        List<RolePermissionGrant> bindings = List.of(
                new RolePermissionGrant(2L, 100L, OperationAction.ALL),
                new RolePermissionGrant(2L, 200L, OperationAction.ALL));
        List<PermissionFact> permissions = List.of(
                new PermissionFact(100L, "COURSE", OperationAction.VIEW, true),
                new PermissionFact(200L, "OA", OperationAction.VIEW, true));

        Set<Long> result = PermissionResolver.resolve(
                List.of(expired), Map.of(2L, role), bindings, permissions, "COURSE", NOW);

        assertThat(result).isEmpty();
    }

    private static Role role(Long id, Long parentId, RoleStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        return Role.restore(id, "R" + id, "角色" + id, RoleType.FUNC,
                parentId, status, null, now, now, 0L, 1L, 1L);
    }
}
