package com.polyauth.api.admin.vo;

import com.polyauth.service.port.account.UserView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 CloneFactory 生成源码和用户视图映射结果。
 */
class CloneFactoryMappingTest {

    /**
     * 生成源码必须包含真实字段赋值，避免只生成空方法。
     */
    @Test
    @DisplayName("FR-49 CloneFactory 生成 UserVO 同名字段映射")
    void generatedFactoryContainsUserViewAssignments() throws Exception {
        Path generated = Path.of(
                "target",
                "generated-sources",
                "annotations",
                "cn",
                "edu",
                "xmu",
                "clonefactory",
                "util",
                "CloneFactory.java");

        assertThat(generated).exists();
        assertThat(Files.readString(generated))
                .contains("copy(UserVO target, UserView source)")
                .contains("target.setRealName(source.getRealName())")
                .contains("target.setAccountStatus(source.getAccountStatus())");
    }

    /**
     * 用户安全视图的全部同名字段都需要完成往返。
     */
    @Test
    @DisplayName("FR-49 CloneFactory 完成 UserVO 字段往返")
    void userViewRoundTripKeepsAllFields() {
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 22, 18, 0);
        UserView view = new UserView();
        view.setId(7L);
        view.setUserNo("T1008");
        view.setRealName("孙晨");
        view.setPhoneMasked("138****5678");
        view.setOrgId(3L);
        view.setPost("教师");
        view.setAccountStatus("NORMAL");
        view.setLeaveTime(fixedTime);
        view.setLastLoginAt(fixedTime);
        view.setIsDeleted(0);
        view.setCreatedAt(fixedTime);
        view.setUpdatedAt(fixedTime);

        UserVO result = UserVO.from(view);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(view);
    }

    /**
     * 管理端纯字段转换视图都应生成 CloneFactory 映射方法。
     */
    @Test
    @DisplayName("FR-49 CloneFactory 覆盖管理端同名字段视图")
    void generatedFactoryContainsAllAdminViewMappings() throws Exception {
        Path generated = Path.of(
                "target",
                "generated-sources",
                "annotations",
                "cn",
                "edu",
                "xmu",
                "clonefactory",
                "util",
                "CloneFactory.java");

        assertThat(Files.readString(generated))
                .contains("copy(SystemVO target, IamSystem source)")
                .contains("copy(ApiVO target, IamApi source)")
                .contains("copy(PermissionVO target, IamPermission source)")
                .contains("copy(RoleVO target, IamRole source)")
                .contains("copy(OrganizationVO target, IamOrg source)");
    }
}
