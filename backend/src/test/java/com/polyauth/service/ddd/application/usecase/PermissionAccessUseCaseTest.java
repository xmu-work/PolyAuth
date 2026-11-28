package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.domain.model.AccountStatus;
import com.polyauth.service.ddd.domain.model.OperationAction;
import com.polyauth.service.ddd.domain.model.PermissionFact;
import com.polyauth.service.ddd.domain.model.Role;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.RolePermissionGrant;
import com.polyauth.service.ddd.domain.model.RoleStatus;
import com.polyauth.service.ddd.domain.model.RoleType;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.repository.PermissionCatalogRepository;
import com.polyauth.service.ddd.domain.repository.RoleGrantRepository;
import com.polyauth.service.ddd.domain.repository.RolePermissionRepository;
import com.polyauth.service.ddd.domain.repository.RoleRepository;
import com.polyauth.service.ddd.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionAccessUseCaseTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private RoleGrantRepository roleGrantRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private PermissionCatalogRepository permissionCatalogRepository;

    private PermissionAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PermissionAccessUseCase(userRepository, roleGrantRepository,
                roleRepository, rolePermissionRepository, permissionCatalogRepository, CLOCK);
    }

    @Test
    void dataViewExpandsToAllViewFacts() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        Role role = Role.restore(3L, "VIEWER", "只读", RoleType.FUNC,
                List.of(), RoleStatus.ACTIVE, null, now, now, 0L, 1L, 1L);
        UserAccount user = UserAccount.restore(1L, "T1008", "hash", "cipher",
                AccountStatus.NORMAL, false, now, now, 0L);
        RoleGrant grant = RoleGrant.grant(1L, 3L, CLOCK.instant().minusSeconds(60),
                null, 9L, null, CLOCK.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleGrantRepository.findByUserId(1L)).thenReturn(List.of(grant));
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(rolePermissionRepository.findByRoleIds(anyCollection()))
                .thenReturn(List.of(new RolePermissionGrant(3L, 10009L, OperationAction.VIEW)));
        when(permissionCatalogRepository.findFacts(anyCollection()))
                .thenReturn(List.of(new PermissionFact(10009L, "ADMIN",
                        OperationAction.VIEW, true)));
        when(permissionCatalogRepository.findViewFacts("ADMIN"))
                .thenReturn(List.of(new PermissionFact(10001L, "ADMIN",
                        OperationAction.VIEW, true)));

        Set<Long> permissions = useCase.permissions(1L, "ADMIN");

        assertThat(permissions).containsExactlyInAnyOrder(10009L, 10001L);
    }

    @Test
    void resolvesPermissionFromActiveParentRole() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        Role child = Role.restore(3L, "TEACHER", "教师", RoleType.FUNC,
                2L, RoleStatus.ACTIVE, null, now, now, 0L, 1L, 1L);
        Role parent = Role.restore(2L, "STAFF", "教职工", RoleType.FUNC,
                List.of(), RoleStatus.ACTIVE, null, now, now, 0L, 1L, 1L);
        UserAccount user = UserAccount.restore(1L, "T1008", "hash", "cipher",
                AccountStatus.NORMAL, false, now, now, 0L);
        RoleGrant grant = RoleGrant.grant(1L, 3L, CLOCK.instant().minusSeconds(60),
                null, 9L, null, CLOCK.instant());
        RolePermissionGrant binding =
                new RolePermissionGrant(2L, 100L, OperationAction.VIEW);
        PermissionFact fact =
                new PermissionFact(100L, "COURSE", OperationAction.VIEW, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleGrantRepository.findByUserId(1L)).thenReturn(List.of(grant));
        when(roleRepository.findAll()).thenReturn(List.of(parent, child));
        when(rolePermissionRepository.findByRoleIds(anyCollection()))
                .thenReturn(List.of(binding));
        when(permissionCatalogRepository.findFacts(anyCollection()))
                .thenReturn(List.of(fact));

        Set<Long> permissions = useCase.permissions(1L, "COURSE");

        assertThat(permissions).containsExactly(100L);
        assertThat(useCase.hasPermission(1L, "COURSE", 100L)).isTrue();
    }
}
