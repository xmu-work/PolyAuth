package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    @DisplayName("新角色默认启用并保存继承关系")
    void createBuildsActiveRole() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);

        Role role = Role.create("PROJECT_MANAGER", "项目经理", RoleType.FUNC,
                "项目管理角色", 12L, 99L, now);

        assertThat(role.roleCode()).isEqualTo("PROJECT_MANAGER");
        assertThat(role.parentId()).isEqualTo(12L);
        assertThat(role.status()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(role.createdBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("角色不能把自己的父角色设置为自己")
    void updateRejectsSelfParent() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        Role role = Role.restore(7L, "AUDITOR", "审计员", RoleType.FUNC,
                null, RoleStatus.ACTIVE, null, now, now, 0L, 1L, 1L);

        assertThatThrownBy(() -> role.update("审计员", null, RoleStatus.ACTIVE, 7L, 1L, now))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("停用角色不能继续作为有效角色")
    void disabledRoleIsNotActive() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        Role role = Role.create("AUDITOR", "审计员", RoleType.FUNC,
                null, null, 1L, now);

        role.update("审计员", null, RoleStatus.DISABLED, null, 1L, now);

        assertThat(role.isActive()).isFalse();
    }
}
