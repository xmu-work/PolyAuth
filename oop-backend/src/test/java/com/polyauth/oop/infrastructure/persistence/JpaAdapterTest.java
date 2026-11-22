package com.polyauth.oop.infrastructure.persistence;

import com.polyauth.oop.domain.model.PermissionFact;
import com.polyauth.oop.domain.model.Role;
import com.polyauth.oop.domain.model.RoleGrant;
import com.polyauth.oop.domain.model.RoleGrantStatus;
import com.polyauth.oop.domain.model.RoleStatus;
import com.polyauth.oop.domain.model.RoleType;
import com.polyauth.oop.domain.repository.PermissionCatalogRepository;
import com.polyauth.oop.domain.repository.RoleGrantRepository;
import com.polyauth.oop.domain.repository.RoleRepository;
import com.polyauth.oop.infrastructure.persistence.adapter.JpaPermissionCatalogRepositoryAdapter;
import com.polyauth.oop.infrastructure.persistence.adapter.JpaRoleGrantRepositoryAdapter;
import com.polyauth.oop.infrastructure.persistence.adapter.JpaRoleRepositoryAdapter;
import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import com.polyauth.oop.infrastructure.persistence.entity.SystemEntity;
import com.polyauth.oop.infrastructure.persistence.repository.PermissionJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.SystemJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OOP 领域端口与 JPA Adapter 的持久化映射。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({
        JpaRoleRepositoryAdapter.class,
        JpaRoleGrantRepositoryAdapter.class,
        JpaPermissionCatalogRepositoryAdapter.class
})
class JpaAdapterTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleGrantRepository roleGrantRepository;

    @Autowired
    private PermissionCatalogRepository permissionCatalogRepository;

    @Autowired
    private SystemJpaRepository systemRepository;

    @Autowired
    private PermissionJpaRepository permissionRepository;

    @Test
    void savesAndRestoresRoleThroughDomainPort() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        Role saved = roleRepository.save(Role.create(
                "AUDITOR", "审计员", RoleType.FUNC, "契约角色", null, 1L, now));

        Role restored = roleRepository.findById(saved.id()).orElseThrow();

        assertThat(restored.roleCode()).isEqualTo("AUDITOR");
        assertThat(restored.status()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(restored.createdBy()).isEqualTo(1L);
    }

    @Test
    void savesAndRestoresRoleGrantThroughDomainPort() {
        Role role = roleRepository.save(Role.create(
                "TEMP", "临时角色", RoleType.FUNC, null, null,
                1L, LocalDateTime.now()));
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        RoleGrant saved = roleGrantRepository.save(RoleGrant.grant(
                5L, role.id(), now, now.plusSeconds(3600), 1L, "临时", now));

        RoleGrant restored = roleGrantRepository
                .findByUserIdAndRoleId(5L, role.id()).orElseThrow();

        assertThat(restored.id()).isEqualTo(saved.id());
        assertThat(restored.status()).isEqualTo(RoleGrantStatus.ACTIVE);
        assertThat(restored.expireTime()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void readsPermissionFactsWithSystemCode() {
        SystemEntity system = new SystemEntity();
        system.setSystemCode("COURSE");
        system.setSystemName("课程系统");
        system.setStatus("ACTIVE");
        system.setIsDeleted(0);
        system.setCreatedAt(LocalDateTime.now());
        system.setUpdatedAt(LocalDateTime.now());
        system.setVersion(0L);
        system = systemRepository.saveAndFlush(system);

        PermissionEntity permission = new PermissionEntity();
        permission.setSystemId(system.getId());
        permission.setParentId(null);
        permission.setPermissionCode("COURSE_VIEW");
        permission.setPermissionName("课程查看");
        permission.setPermissionType("MENU");
        permission.setRoutePath("/course");
        permission.setAction("VIEW");
        permission.setSortNo(1);
        permission.setStatus("ACTIVE");
        permission.setIsDeleted(0);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permission = permissionRepository.saveAndFlush(permission);

        List<PermissionFact> facts = permissionCatalogRepository.findFacts(
                Set.of(permission.getId()));

        assertThat(facts).extracting(PermissionFact::systemCode)
                .containsExactly("COURSE");
        assertThat(permissionCatalogRepository.findFact(permission.getId())).isPresent();
    }
}
