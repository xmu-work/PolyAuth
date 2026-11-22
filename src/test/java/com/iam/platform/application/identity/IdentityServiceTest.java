package com.iam.platform.application.identity;

import com.iam.platform.TestFixtures;
import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.infrastructure.crypto.CryptoComponent;
import com.iam.platform.infrastructure.persistence.entity.IamApi;
import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import com.iam.platform.infrastructure.persistence.entity.IamPermission;
import com.iam.platform.infrastructure.persistence.entity.IamSystem;
import com.iam.platform.infrastructure.persistence.entity.IamUser;
import com.iam.platform.infrastructure.persistence.repository.IamApiRepository;
import com.iam.platform.infrastructure.persistence.repository.IamOrgRepository;
import com.iam.platform.infrastructure.persistence.repository.IamPermissionRepository;
import com.iam.platform.infrastructure.persistence.repository.IamSystemRepository;
import com.iam.platform.infrastructure.persistence.repository.IamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamOrgRepository orgRepository;
    @Mock
    private IamApiRepository apiRepository;
    @Mock
    private IamSystemRepository systemRepository;
    @Mock
    private IamPermissionRepository permissionRepository;
    @Mock
    private DecisionService decisionService;

    private final CryptoComponent crypto = TestFixtures.crypto();
    private IdentityService identityService;
    private IamSystem system;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(userRepository, orgRepository, apiRepository,
                systemRepository, permissionRepository, decisionService, crypto);
        system = new IamSystem();
        system.setId(10L);
        system.setSystemCode("course-project-management");
        system.setStatus(IamSystem.STATUS_ACTIVE);
        system.setIsDeleted(0);
    }

    @Test
    @DisplayName("FR-38 当前身份返回有效角色与前端权限码")
    void currentUserMapsRolesAndPermissionCodes() {
        IamUser user = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        IamPermission permission = new IamPermission();
        permission.setId(100L);
        permission.setSystemId(10L);
        permission.setPermissionCode("CV");
        permission.setIsDeleted(0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(decisionService.getUserPermissions(7L, "course-project-management"))
                .thenReturn(Set.of(100L));
        when(decisionService.getUserRoleCodes(7L)).thenReturn(Set.of("teacher", "admin"));
        when(permissionRepository.findAllById(Set.of(100L))).thenReturn(List.of(permission));

        IdentityProfile profile = identityService.getCurrentUser(7L, "course-project-management");

        assertThat(profile.subject()).isEqualTo("u7");
        assertThat(profile.no()).isEqualTo("u7");
        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.roles()).containsExactly("admin", "teacher");
        assertThat(profile.permissions()).containsExactly("CV");
    }

    @Test
    @DisplayName("FR-09 部门查询只返回直接下级并带 hasChildren")
    void departmentPageReturnsDirectChildren() {
        IamOrg child = org(2L, 1L, "CS", "计算机学院");
        IamOrg nested = org(3L, 2L, "SE", "软件工程");
        when(orgRepository.findById(1L)).thenReturn(Optional.of(org(1L, null, "ROOT", "学校")));
        when(orgRepository.findByParentId(1L)).thenReturn(List.of(child));
        when(orgRepository.findByParentId(2L)).thenReturn(List.of(nested));

        IdentityPageSlice<IdentityDepartmentData> page =
                identityService.pageDepartments(null, 1L, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(
                new IdentityDepartmentData(2L, "CS", "计算机学院", 1L, true));
    }

    @Test
    @DisplayName("FR-37 operationId 映射到 API 权限并 fail-closed")
    void apiOperationChecksMappedPermission() {
        IamApi api = new IamApi();
        api.setId(30L);
        api.setSystemId(10L);
        api.setApiCode("listAdminCourses");
        api.setPermissionId(100L);
        api.setStatus(IamApi.STATUS_ACTIVE);
        api.setIsDeleted(0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                TestFixtures.user(7L, "NORMAL", crypto, "pwd")));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(apiRepository.findBySystemIdAndApiCode(10L, "listAdminCourses"))
                .thenReturn(Optional.of(api));
        when(decisionService.hasPermission(7L, "course-project-management", 100L))
                .thenReturn(true);

        assertThat(identityService.hasApiOperation(7L, "course-project-management",
                "listAdminCourses")).isTrue();
    }

    @Test
    @DisplayName("FR-02 无 TEC 权限不能查询统一身份用户")
    void userLookupRequiresTeacherManagementPermission() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                TestFixtures.user(7L, "NORMAL", crypto, "pwd")));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "TEC"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> identityService.lookupUser(
                7L, "course-project-management", "T1008"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ErrorCode.NO_FUNCTION_PERM.getCode());
    }

    private IamOrg org(Long id, Long parentId, String code, String name) {
        IamOrg org = new IamOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setOrgCode(code);
        org.setOrgName(name);
        org.setSortNo(0);
        org.setStatus(IamOrg.STATUS_ACTIVE);
        org.setIsDeleted(0);
        return org;
    }
}
