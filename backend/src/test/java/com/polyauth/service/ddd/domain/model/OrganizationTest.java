package com.polyauth.service.ddd.domain.model;

import com.polyauth.service.ddd.domain.exception.DomainRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证组织聚合的层级和移动不变量。
 */
class OrganizationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 22, 20, 0);

    @Test
    @DisplayName("FR-08 组织类型必须与层级一致")
    void rejectsMismatchedOrganizationLevel() {
        assertThatThrownBy(() -> Organization.create(
                "ORG-1", "错误组织", OrganizationType.BRANCH, 3,
                null, "0", 0, 1L, NOW))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("层级");
    }

    @Test
    @DisplayName("FR-09 组织不能移动到自身或更低层级节点下")
    void rejectsInvalidReparent() {
        Organization branch = persisted(1L, null, "0", "BRANCH-1",
                OrganizationType.BRANCH, NOW);
        Organization department = persisted(2L, 1L, "0,1", "DEPT-1",
                OrganizationType.DEPT, NOW);

        assertThatThrownBy(() -> branch.reparent(department, 9L, NOW))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("层级");
    }

    @Test
    @DisplayName("FR-09 移动到新父节点后祖先路径同步更新")
    void computesNewAncestorsPath() {
        Organization branch = persisted(2L, 1L, "0,1", "BRANCH-1",
                OrganizationType.BRANCH, NOW);
        Organization hq = persisted(3L, null, "0", "HQ-2",
                OrganizationType.HQ, NOW);

        Organization moved = branch.reparent(hq, 9L, NOW.plusMinutes(1));

        assertThat(moved.parentId()).isEqualTo(3L);
        assertThat(moved.ancestors()).isEqualTo("0,3");
        assertThat(moved.updatedBy()).isEqualTo(9L);
    }

    private Organization persisted(Long id, Long parentId, String ancestors,
                                   String code, OrganizationType type,
                                   LocalDateTime now) {
        return Organization.restore(id, parentId, ancestors, code, code,
                type, null, 0, OrganizationStatus.ACTIVE, false,
                now, now, 0L, 1L, 1L);
    }
}
