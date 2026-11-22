package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.command.CreateRoleCommand;
import com.polyauth.oop.application.command.GrantRoleCommand;
import com.polyauth.oop.application.command.UpdateRoleCommand;
import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.domain.model.AccountStatus;
import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RoleStatus;
import com.polyauth.oop.domain.model.RoleType;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.RoleGrantRepository;
import com.polyauth.oop.domain.repository.RolePermissionRepository;
import com.polyauth.oop.domain.repository.RoleRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import com.polyauth.oop.domain.repository.PermissionCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleManagementUseCaseTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryRoleRepository roles;
    private InMemoryRoleGrantRepository grants;
    private InMemoryUserAccountRepository users;
    private RoleManagementUseCase useCase;

    @BeforeEach
    void setUp() {
        roles = new InMemoryRoleRepository();
        grants = new InMemoryRoleGrantRepository();
        users = new InMemoryUserAccountRepository();
        useCase = new RoleManagementUseCase(
                roles, users, grants, new EmptyRolePermissionRepository(),
                new EmptyPermissionCatalogRepository(), new NoOpAuditTrail(), CLOCK);
    }

    @Test
    void rejectsDuplicateRoleCode() {
        roles.save(Role.create("AUDITOR", "审计员", RoleType.FUNC, null, null, 1L, now()));

        assertThatThrownBy(() -> useCase.createRole(
                new CreateRoleCommand("AUDITOR", "第二审计员", "FUNC", null, null, 1L)))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).errorCode())
                .isEqualTo(ApplicationErrorCode.RESOURCE_DUPLICATE);
    }

    @Test
    void rejectsCycleWhenChangingParent() {
        Role child = roles.save(Role.create(
                "CHILD", "子角色", RoleType.FUNC, null, null, 1L, now()));
        Role parent = roles.save(Role.create(
                "PARENT", "父角色", RoleType.FUNC, null, child.id(), 1L, now()));

        assertThatThrownBy(() -> useCase.updateRole(new UpdateRoleCommand(
                child.id(), "子角色", null, "ACTIVE", parent.id(), 1L)))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).errorCode())
                .isEqualTo(ApplicationErrorCode.ROLE_INHERIT_CYCLE);
    }

    @Test
    void grantsRoleToActiveUserAndRole() {
        Role role = roles.save(Role.create(
                "AUDITOR", "审计员", RoleType.FUNC, null, null, 1L, now()));
        users.save(UserAccount.restore(
                5L, "zhangsan", "hash", "cipher", AccountStatus.NORMAL,
                false, now(), now(), 0L));

        RoleGrant grant = useCase.grantRole(
                new GrantRoleCommand(5L, role.id(), null, 1L, "临时审计"));

        assertThat(grant.status().name()).isEqualTo("ACTIVE");
        assertThat(grants.findByUserIdAndRoleId(5L, role.id())).contains(grant);
    }

    @Test
    void rejectsExpiryThatIsNotAfterCurrentTime() {
        Role role = roles.save(Role.create(
                "AUDITOR", "审计员", RoleType.FUNC, null, null, 1L, now()));
        users.save(UserAccount.restore(
                5L, "zhangsan", "hash", "cipher", AccountStatus.NORMAL,
                false, now(), now(), 0L));

        assertThatThrownBy(() -> useCase.grantRole(
                new GrantRoleCommand(5L, role.id(), CLOCK.instant(), 1L, null)))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).errorCode())
                .isEqualTo(ApplicationErrorCode.PARAM_INVALID);
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(CLOCK.instant(), Clock.systemDefaultZone().getZone());
    }

    private static final class InMemoryRoleRepository implements RoleRepository {

        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Role> data = new HashMap<>();

        @Override
        public Optional<Role> findById(Long roleId) {
            return Optional.ofNullable(data.get(roleId));
        }

        @Override
        public Optional<Role> findByCode(String roleCode) {
            return data.values().stream()
                    .filter(role -> role.roleCode().equals(roleCode))
                    .findFirst();
        }

        @Override
        public List<Role> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Role save(Role role) {
            Long id = role.id() == null ? sequence.incrementAndGet() : role.id();
            Role restored = Role.restore(id, role.roleCode(), role.roleName(), role.roleType(),
                    role.parentId(), role.status(), role.description(), role.createdAt(),
                    role.updatedAt(), role.version(), role.createdBy(), role.updatedBy());
            data.put(id, restored);
            return restored;
        }
    }

    private static final class InMemoryUserAccountRepository implements UserAccountRepository {

        private final Map<Long, UserAccount> data = new HashMap<>();

        @Override
        public Optional<UserAccount> findById(Long userId) {
            return Optional.ofNullable(data.get(userId));
        }

        @Override
        public Optional<UserAccount> findByUserNo(String userNo) {
            return data.values().stream()
                    .filter(user -> user.userNo().equals(userNo))
                    .findFirst();
        }

        @Override
        public UserAccount save(UserAccount user) {
            data.put(user.id(), user);
            return user;
        }
    }

    private static final class InMemoryRoleGrantRepository implements RoleGrantRepository {

        private final AtomicLong sequence = new AtomicLong();
        private final List<RoleGrant> data = new ArrayList<>();

        @Override
        public Optional<RoleGrant> findByUserIdAndRoleId(Long userId, Long roleId) {
            return data.stream()
                    .filter(grant -> grant.userId().equals(userId)
                            && grant.roleId().equals(roleId))
                    .findFirst();
        }

        @Override
        public List<RoleGrant> findByUserId(Long userId) {
            return data.stream().filter(grant -> grant.userId().equals(userId)).toList();
        }

        @Override
        public RoleGrant save(RoleGrant grant) {
            if (grant.id() != null) {
                data.removeIf(item -> item.id().equals(grant.id()));
            }
            Long id = grant.id() == null ? sequence.incrementAndGet() : grant.id();
            RoleGrant restored = RoleGrant.restore(id, grant.userId(), grant.roleId(),
                    grant.effectiveTime(), grant.expireTime(), grant.grantBy(), grant.reason(),
                    grant.createdAt(), grant.updatedAt(), grant.revokedAt(), grant.status(),
                    grant.version());
            data.add(restored);
            return restored;
        }
    }

    private static final class EmptyRolePermissionRepository implements RolePermissionRepository {

        @Override
        public List<com.polyauth.oop.domain.model.RolePermissionGrant> findByRoleId(Long roleId) {
            return List.of();
        }

        @Override
        public List<com.polyauth.oop.domain.model.RolePermissionGrant> findByRoleIds(
                java.util.Collection<Long> roleIds) {
            return List.of();
        }

        @Override
        public void bind(Long roleId, Long permissionId,
                         com.polyauth.oop.domain.model.OperationAction allowedAction,
                         Long operatorId) {
        }

        @Override
        public void unbind(Long roleId, Long permissionId) {
        }
    }

    private static final class EmptyPermissionCatalogRepository implements PermissionCatalogRepository {

        @Override
        public Optional<com.polyauth.oop.domain.model.PermissionFact> findFact(Long permissionId) {
            return Optional.empty();
        }

        @Override
        public List<com.polyauth.oop.domain.model.PermissionFact> findFacts(
                java.util.Collection<Long> permissionIds) {
            return List.of();
        }
    }

    private static final class NoOpAuditTrail implements AuditTrail {

        @Override
        public void recordLogin(Long userId, String userNo, String systemCode,
                                boolean success, String error, String clientIp,
                                String requestId) {
        }

        @Override
        public void recordRequest(Long userId, String userNo, String systemCode,
                                  String action, String target, String clientIp,
                                  boolean success, String error, String requestId) {
        }

        @Override
        public void recordPermissionChange(Long operatorId, String systemCode,
                                           String action, String target,
                                           Object before, Object after) {
        }
    }
}
