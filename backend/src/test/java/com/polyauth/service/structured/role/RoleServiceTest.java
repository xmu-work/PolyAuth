package com.polyauth.service.structured.role;

import com.polyauth.TestFixtures;
import com.polyauth.service.structured.decision.DecisionService;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import com.polyauth.infrastructure.persistence.structured.entity.IamRoleParent;
import com.polyauth.infrastructure.persistence.structured.entity.IamRolePermission;
import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;
import com.polyauth.infrastructure.persistence.structured.repository.IamPermissionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRolePermissionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleExclusionRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleParentRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamRoleRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRoleRepository;
import com.polyauth.infrastructure.cache.BusinessLockExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private IamRoleRepository roleRepository;
    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamUserRoleRepository userRoleRepository;
    @Mock
    private IamPermissionRepository permissionRepository;
    @Mock
    private IamRolePermissionRepository rolePermissionRepository;
    @Mock
    private IamRoleParentRepository roleParentRepository;
    @Mock
    private IamRoleExclusionRepository roleExclusionRepository;
    @Mock
    private DecisionService decisionService;
    @Mock
    private LogService logService;
    @Mock
    private BusinessLockExecutor lockExecutor;
    @InjectMocks
    private RoleService roleService;

    /**
     * 单元测试中同步执行受锁保护的业务动作。
     */
    @BeforeEach
    void setUpLockExecutor() {
        lenient().when(lockExecutor.execute(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    private static int code(Throwable e) {
        return ((BusinessException) e).getCode();
    }

    @Test
    void createAndQueryRoles() {
        when(roleRepository.findByRoleCode("HR")).thenReturn(Optional.empty());
        when(roleRepository.save(any(IamRole.class))).thenAnswer(inv -> inv.getArgument(0));

        IamRole created = roleService.createRole("HR", "人事", "FUNC", "人事专员");

        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getRoleType()).isEqualTo("FUNC");
    }

    @Test
    @DisplayName("创建角色支持多个父角色")
    void createRoleSupportsMultipleParents() {
        when(roleRepository.findByRoleCode("MULTI")).thenReturn(Optional.empty());
        when(roleRepository.save(any(IamRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(TestFixtures.role(2L, "ACTIVE")));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(TestFixtures.role(3L, "ACTIVE")));

        IamRole created = roleService.createRole("MULTI", "多继承角色", "FUNC", null,
                List.of(2L, 3L));

        assertThat(created.getParentId()).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<IamRoleParent>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(roleParentRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(IamRoleParent::getParentId)
                .containsExactly(2L, 3L);
    }

    @Test
    void grantRoleCreatesActiveAssignment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                TestFixtures.user(1L, "NORMAL", TestFixtures.crypto(), "p")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(TestFixtures.role(2L, "ACTIVE")));
        when(userRoleRepository.findByUserIdAndRoleId(1L, 2L)).thenReturn(Optional.empty());
        when(userRoleRepository.save(any(IamUserRole.class))).thenAnswer(inv -> inv.getArgument(0));

        IamUserRole grant = roleService.grantRole(1L, 2L, null, null, "入职");

        assertThat(grant.getStatus()).isEqualTo("ACTIVE");
        assertThat(grant.getRoleId()).isEqualTo(2L);
        assertThat(grant.getGrantReason()).isEqualTo("入职");
    }

    @Test
    void grantRejectsDuplicateAssignment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                TestFixtures.user(1L, "NORMAL", TestFixtures.crypto(), "p")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(TestFixtures.role(2L, "ACTIVE")));
        when(userRoleRepository.findByUserIdAndRoleId(1L, 2L))
                .thenReturn(Optional.of(TestFixtures.grant(1L, 2L, "ACTIVE")));

        assertThatThrownBy(() -> roleService.grantRole(1L, 2L, null, null, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.GRANT_DUPLICATE.getCode()));
    }

    @Test
    void revokeMarksAssignmentRevoked() {
        IamUserRole grant = TestFixtures.grant(1L, 2L, "ACTIVE");
        when(userRoleRepository.findByUserIdAndRoleId(1L, 2L)).thenReturn(Optional.of(grant));
        when(userRoleRepository.save(grant)).thenReturn(grant);

        roleService.revokeRole(1L, 2L, 9L);

        assertThat(grant.getStatus()).isEqualTo("REVOKED");
        verify(userRoleRepository).save(grant);
    }

    @Test
    void bindPermissionsSkipsAlreadyBoundAndSavesNew() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(TestFixtures.role(1L, "ACTIVE")));
        IamRolePermission existing = binding(1L, 10L);
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of(existing));
        when(permissionRepository.findById(any())).thenAnswer(inv -> {
            IamPermission permission = new IamPermission();
            permission.setId(inv.getArgument(0));
            permission.setStatus("ACTIVE");
            permission.setIsDeleted(0);
            return Optional.of(permission);
        });

        roleService.bindRolePermissions(1L, List.of(10L, 11L, 11L), "VIEW", 9L);

        ArgumentCaptor<IamRolePermission> saved = ArgumentCaptor.forClass(IamRolePermission.class);
        verify(rolePermissionRepository).save(saved.capture());
        assertThat(saved.getValue().getPermissionId()).isEqualTo(11L);
        assertThat(saved.getValue().getAllowedAction()).isEqualTo("VIEW");
    }

    @Test
    void grantRejectsRoleBeyondOperatorPermissions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                TestFixtures.user(1L, "NORMAL", TestFixtures.crypto(), "p")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(TestFixtures.role(2L, "ACTIVE")));
        when(userRoleRepository.findByUserIdAndRoleId(1L, 2L)).thenReturn(Optional.empty());
        when(roleRepository.findAllById(any())).thenReturn(List.of(TestFixtures.role(2L, "ACTIVE")));
        when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of(binding(2L, 50L)));
        when(decisionService.getUserPermissions(9L, "ADMIN")).thenReturn(Set.of());

        assertThatThrownBy(() -> roleService.grantRole(1L, 2L, null, 9L, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.NO_FUNCTION_PERM.getCode()));
    }

    /**
     * 验证会计和出纳等互斥角色不能同时授予同一用户。
     */
    @Test
    @DisplayName("R14 授予互斥角色时拒绝")
    void grantRejectsMutuallyExclusiveRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                TestFixtures.user(1L, "NORMAL", TestFixtures.crypto(), "p")));
        when(roleRepository.findById(11L)).thenReturn(Optional.of(TestFixtures.role(11L, "ACTIVE")));
        when(userRoleRepository.findByUserIdAndRoleId(1L, 11L)).thenReturn(Optional.empty());
        IamUserRole existing = TestFixtures.grant(1L, 10L, "ACTIVE");
        when(userRoleRepository.findByUserIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(existing));
        com.polyauth.infrastructure.persistence.structured.entity.IamRoleExclusion exclusion =
                new com.polyauth.infrastructure.persistence.structured.entity.IamRoleExclusion();
        exclusion.setRoleId(10L);
        exclusion.setExcludedRoleId(11L);
        when(roleExclusionRepository.findByRoleIdOrExcludedRoleId(11L, 11L))
                .thenReturn(List.of(exclusion));

        assertThatThrownBy(() -> roleService.grantRole(1L, 11L, null, null, null))
                .satisfies(error -> assertThat(code(error))
                        .isEqualTo(ErrorCode.ROLE_EXCLUSION_CONFLICT.getCode()));
    }

    /**
     * 验证角色可以设置父角色并禁止循环继承。
     */
    @Test
    @DisplayName("R13 角色继承禁止循环")
    void roleHierarchyRejectsCycle() {
        when(roleRepository.findByRoleCode("LEAD")).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(TestFixtures.role(2L, "ACTIVE")));
        when(roleRepository.save(any(IamRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IamRole child = roleService.createRole("LEAD", "项目经理", "FUNC", "项目角色", 2L);

        assertThat(child.getParentId()).isEqualTo(2L);
    }

    /**
     * 验证修改父角色时检测并拒绝循环继承。
     */
    @Test
    @DisplayName("R13 修改继承关系时拒绝循环")
    void updateRoleRejectsInheritanceCycle() {
        IamRole child = TestFixtures.role(2L, "ACTIVE");
        child.setParentId(3L);
        IamRole parent = TestFixtures.role(3L, "ACTIVE");
        parent.setParentId(2L);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(child));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> roleService.updateRole(2L, "子角色", null, "ACTIVE", 3L, 1L))
                .satisfies(error -> assertThat(code(error))
                        .isEqualTo(ErrorCode.ROLE_INHERIT_CYCLE.getCode()));
        verify(roleRepository, never()).save(any());
    }

    /**
     * 验证建立互斥关系前检查已有用户是否形成冲突。
     */
    @Test
    @DisplayName("R14 禁止建立会产生既有冲突的互斥关系")
    void bindExclusionRejectsExistingUsersWithBothRoles() {
        when(roleRepository.findById(10L)).thenReturn(Optional.of(TestFixtures.role(10L, "ACTIVE")));
        when(roleRepository.findById(11L)).thenReturn(Optional.of(TestFixtures.role(11L, "ACTIVE")));
        when(userRoleRepository.findUserIdsWithBothActiveRoles(10L, 11L)).thenReturn(List.of(5L));

        assertThatThrownBy(() -> roleService.bindRoleExclusion(10L, 11L, 1L))
                .satisfies(error -> assertThat(code(error))
                        .isEqualTo(ErrorCode.ROLE_EXCLUSION_CONFLICT.getCode()));
        verify(roleExclusionRepository, never()).save(any());
    }

    private static IamRolePermission binding(Long roleId, Long permissionId) {
        IamRolePermission binding = new IamRolePermission();
        binding.setRoleId(roleId);
        binding.setPermissionId(permissionId);
        return binding;
    }
}
