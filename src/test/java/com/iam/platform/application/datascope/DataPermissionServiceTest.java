package com.iam.platform.application.datascope;

import com.iam.platform.application.api.ApiService;
import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.infrastructure.persistence.entity.IamApi;
import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import com.iam.platform.infrastructure.persistence.entity.IamRole;
import com.iam.platform.infrastructure.persistence.entity.IamRoleDataScope;
import com.iam.platform.infrastructure.persistence.entity.IamUserRole;
import com.iam.platform.infrastructure.persistence.repository.IamOrgRepository;
import com.iam.platform.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.iam.platform.infrastructure.persistence.repository.IamRoleRepository;
import com.iam.platform.infrastructure.persistence.repository.IamUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * RBAC3 功能权限与数据权限联合判定测试。
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionServiceTest {

    @Mock
    private DecisionService decisionService;
    @Mock
    private ApiService apiService;
    @Mock
    private IamRoleDataScopeRepository roleDataScopeRepository;
    @Mock
    private IamRoleRepository roleRepository;
    @Mock
    private IamOrgRepository orgRepository;
    @Mock
    private IamUserRoleRepository userRoleRepository;

    private DataPermissionService service;
    private IamUserRole grant;
    private IamRole role;

    /**
     * 初始化有效用户角色及 API 功能权限映射。
     */
    @BeforeEach
    void setUp() {
        service = new DataPermissionService(decisionService, apiService, roleDataScopeRepository,
                roleRepository, orgRepository, userRoleRepository);
        grant = new IamUserRole();
        grant.setUserId(1L);
        grant.setRoleId(2L);
        grant.setStatus("ACTIVE");
        grant.setEffectiveTime(LocalDateTime.now().minusDays(1));
        role = new IamRole();
        role.setId(2L);
        role.setRoleType("FUNC");
        role.setStatus("ACTIVE");
        role.setIsDeleted(0);
        IamApi api = new IamApi();
        api.setId(30L);
        api.setPermissionId(200L);
        lenient().when(apiService.getApi(30L)).thenReturn(api);
        lenient().when(userRoleRepository.findByUserIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(grant));
        lenient().when(roleRepository.findAllById(any())).thenReturn(List.of(role));
    }

    /**
     * 验证两项权限同时通过才允许资源访问。
     */
    @Test
    @DisplayName("R24 功能权限与数据权限联合放行")
    void allowsOnlyWhenFunctionAndDataBothPass() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        IamRoleDataScope all = scope(11L, "ALL", null, null, null);
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L))).thenReturn(List.of(all));

        ResourceDecision decision = service.authorize(1L, "ADMIN", 30L, 9L, 99L, 7L);

        assertThat(decision.isFunctionAllowed()).isTrue();
        assertThat(decision.isDataAllowed()).isTrue();
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMatchedScopeIds()).containsExactly(11L);
    }

    /**
     * 验证功能权限拒绝时即使数据范围满足也不放行。
     */
    @Test
    @DisplayName("R24 缺少功能权限时拒绝数据访问")
    void rejectsWhenFunctionPermissionMissing() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(false);

        ResourceDecision decision = service.authorize(1L, "ADMIN", 30L, null, null, 1L);

        assertThat(decision.isFunctionAllowed()).isFalse();
        assertThat(decision.isAllowed()).isFalse();
    }

    /**
     * 验证部门范围覆盖自身及下级组织，不覆盖其他分公司。
     */
    @Test
    @DisplayName("R22/R26 部门范围按组织子树匹配")
    void departmentScopeMatchesDescendantOrganization() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L)))
                .thenReturn(List.of(scope(12L, "DEPT", 8L, null, null)));
        IamOrg target = new IamOrg();
        target.setId(81L);
        target.setAncestors("0,1,8");
        target.setStatus("ACTIVE");
        target.setIsDeleted(0);
        when(orgRepository.findById(81L)).thenReturn(Optional.of(target));

        ResourceDecision allowed = service.authorize(1L, "ADMIN", 30L, 81L, null, null);
        ResourceDecision denied = service.authorize(1L, "ADMIN", 30L, 120L, null, null);

        assertThat(allowed.isAllowed()).isTrue();
        assertThat(denied.isAllowed()).isFalse();
    }

    /**
     * 验证项目范围只匹配相同项目标识。
     */
    @Test
    @DisplayName("R26 项目范围按项目 ID 精确匹配")
    void projectScopeMatchesExactProject() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L)))
                .thenReturn(List.of(scope(13L, "PROJECT", null, 100L, null)));

        ResourceDecision allowed = service.authorize(1L, "ADMIN", 30L, null, 100L, null);
        ResourceDecision denied = service.authorize(1L, "ADMIN", 30L, null, 101L, null);

        assertThat(allowed.isAllowed()).isTrue();
        assertThat(denied.isAllowed()).isFalse();
    }

    /**
     * 验证本人范围只匹配当前用户。
     */
    @Test
    @DisplayName("R26 本人范围按用户 ID 匹配")
    void selfScopeMatchesOnlyCurrentUser() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L)))
                .thenReturn(List.of(scope(14L, "SELF", null, null, null)));

        ResourceDecision allowed = service.authorize(1L, "ADMIN", 30L, null, null, 1L);
        ResourceDecision denied = service.authorize(1L, "ADMIN", 30L, null, null, 2L);

        assertThat(allowed.isAllowed()).isTrue();
        assertThat(denied.isAllowed()).isFalse();
    }

    /**
     * 验证到期和未生效的数据范围授权均不参与判定。
     */
    @Test
    @DisplayName("R25 过期数据范围自动失效")
    void expiredScopeDoesNotMatch() {
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        IamRoleDataScope expired = scope(15L, "ALL", null, null, LocalDateTime.now().minusMinutes(1));
        IamRoleDataScope future = scope(16L, "ALL", null, null, null);
        future.setEffectiveTime(LocalDateTime.now().plusDays(1));
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L)))
                .thenReturn(List.of(expired, future));

        ResourceDecision decision = service.authorize(1L, "ADMIN", 30L, null, null, 1L);

        assertThat(decision.isDataAllowed()).isFalse();
        assertThat(decision.getMatchedScopeIds()).isEmpty();
    }

    /**
     * 验证统一鉴权接口可返回当前生效的数据范围。
     */
    @Test
    @DisplayName("R33 查询用户生效的数据范围")
    void returnsEffectiveDataScopes() {
        IamRoleDataScope active = scope(20L, "DEPT", 8L, null, null);
        IamRoleDataScope expired = scope(21L, "ALL", null, null, LocalDateTime.now().minusMinutes(1));
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L)))
                .thenReturn(List.of(active, expired));

        List<EffectiveDataScope> scopes = service.getEffectiveDataScopes(1L, "ADMIN");

        assertThat(scopes).hasSize(1);
        assertThat(scopes.get(0).getBindingId()).isEqualTo(20L);
        assertThat(scopes.get(0).getScopeType()).isEqualTo("DEPT");
    }

    /**
     * 验证父角色的数据范围会沿角色继承链传递给子角色用户。
     */
    @Test
    @DisplayName("R13 继承角色合并父角色数据范围")
    void inheritedRoleContributesDataScope() {
        role.setParentId(3L);
        IamRole parent = new IamRole();
        parent.setId(3L);
        parent.setRoleType("FUNC");
        parent.setStatus("ACTIVE");
        parent.setIsDeleted(0);
        when(decisionService.hasPermission(1L, "ADMIN", 200L)).thenReturn(true);
        when(roleRepository.findAllById(Set.of(2L))).thenReturn(List.of(role));
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(parent));
        IamRoleDataScope parentScope = scope(30L, "ALL", null, null, null);
        parentScope.setRoleId(3L);
        when(roleDataScopeRepository.findByRoleIdIn(Set.of(2L, 3L)))
                .thenReturn(List.of(parentScope));

        ResourceDecision decision = service.authorize(1L, "ADMIN", 30L, null, null, null);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMatchedScopeIds()).containsExactly(30L);
    }

    private IamRoleDataScope scope(Long id, String type, Long orgId, Long projectId,
                                   LocalDateTime expireTime) {
        IamRoleDataScope scope = new IamRoleDataScope();
        scope.setId(id);
        scope.setRoleId(2L);
        scope.setScopeType(type);
        scope.setOrgId(orgId);
        scope.setProjectId(projectId);
        scope.setStatus("ACTIVE");
        scope.setEffectiveTime(LocalDateTime.now().minusDays(1));
        scope.setExpireTime(expireTime);
        return scope;
    }
}
