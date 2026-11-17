package com.iam.platform.application.datascope;

import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.application.log.LogService;
import com.iam.platform.infrastructure.cache.PermissionCacheComponent;
import com.iam.platform.infrastructure.cache.BusinessLockExecutor;
import com.iam.platform.infrastructure.persistence.entity.IamDataScope;
import com.iam.platform.infrastructure.persistence.entity.IamOrg;
import com.iam.platform.infrastructure.persistence.entity.IamRole;
import com.iam.platform.infrastructure.persistence.entity.IamRoleDataScope;
import com.iam.platform.infrastructure.persistence.repository.IamDataScopeRepository;
import com.iam.platform.infrastructure.persistence.repository.IamOrgRepository;
import com.iam.platform.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.iam.platform.infrastructure.persistence.repository.IamRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * RBAC3 数据范围管理测试。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @Mock
    private IamDataScopeRepository dataScopeRepository;
    @Mock
    private IamRoleDataScopeRepository roleDataScopeRepository;
    @Mock
    private IamRoleRepository roleRepository;
    @Mock
    private IamOrgRepository orgRepository;
    @Mock
    private LogService logService;
    @Mock
    private PermissionCacheComponent permissionCache;
    @Mock
    private BusinessLockExecutor lockExecutor;
    @InjectMocks
    private DataScopeService service;

    /**
     * 单元测试中直接执行锁内动作。
     */
    @BeforeEach
    void setUpLockExecutor() {
        lenient().when(lockExecutor.execute(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    /**
     * 验证数据范围字典创建为启用状态且规范化非必填排序号。
     */
    @Test
    @DisplayName("R22 创建数据范围字典")
    void createsActiveDataScopeDictionary() {
        when(dataScopeRepository.findByScopeCodeAndIsDeleted("DEPT_SCOPE", 0))
                .thenReturn(Optional.empty());
        when(dataScopeRepository.save(any(IamDataScope.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IamDataScope created = service.createDataScope("DEPT_SCOPE", "部门范围", "按部门过滤", null);

        assertThat(created.getScopeCode()).isEqualTo("DEPT_SCOPE");
        assertThat(created.getSortNo()).isZero();
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getIsDeleted()).isZero();
    }

    /**
     * 验证角色数据范围授权校验角色、组织类型和有效期。
     */
    @Test
    @DisplayName("R22/R25 绑定角色部门范围并保留临时授权有效期")
    void bindsDepartmentScopeWithExpiry() {
        IamRole role = role(2L);
        IamOrg department = new IamOrg();
        department.setId(8L);
        department.setOrgType("DEPT");
        department.setStatus("ACTIVE");
        department.setIsDeleted(0);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(orgRepository.findById(8L)).thenReturn(Optional.of(department));
        when(roleDataScopeRepository.findByRoleId(2L)).thenReturn(List.of());
        when(roleDataScopeRepository.save(any(IamRoleDataScope.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime expiry = LocalDateTime.now().plusDays(3);

        IamRoleDataScope binding = service.bindRoleDataScope(2L, "DEPT", 8L, null, expiry, 1L);

        assertThat(binding.getScopeType()).isEqualTo("DEPT");
        assertThat(binding.getOrgId()).isEqualTo(8L);
        assertThat(binding.getExpireTime()).isEqualTo(expiry);
        assertThat(binding.getStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 验证缺少目标组织或目标组织类型错误时拒绝授权。
     */
    @Test
    @DisplayName("R22 数据范围目标与类型不一致时拒绝")
    void rejectsInvalidScopeTarget() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L)));

        assertThatThrownBy(() -> service.bindRoleDataScope(2L, "DEPT", null, null, null, 1L))
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.DATA_SCOPE_INVALID.getCode()));

        IamOrg projectOrg = new IamOrg();
        projectOrg.setId(8L);
        projectOrg.setOrgType("PROJECT");
        projectOrg.setStatus("ACTIVE");
        projectOrg.setIsDeleted(0);
        when(orgRepository.findById(8L)).thenReturn(Optional.of(projectOrg));

        assertThatThrownBy(() -> service.bindRoleDataScope(2L, "DEPT", 8L, null, null, 1L))
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.DATA_SCOPE_INVALID.getCode()));
        verify(roleDataScopeRepository, never()).save(any());
    }

    /**
     * 验证回收操作保留授权记录并将状态改为 REVOKED。
     */
    @Test
    @DisplayName("R28 回收角色数据范围")
    void revokeKeepsAuditFriendlyRecord() {
        IamRoleDataScope binding = new IamRoleDataScope();
        binding.setId(9L);
        binding.setStatus("ACTIVE");
        when(roleDataScopeRepository.findById(9L)).thenReturn(Optional.of(binding));
        when(roleDataScopeRepository.save(binding)).thenReturn(binding);

        service.revokeRoleDataScope(9L, 1L);

        assertThat(binding.getStatus()).isEqualTo("REVOKED");
        verify(roleDataScopeRepository).save(binding);
    }

    /**
     * 验证已回收的相同范围再次授权时复用原记录，避免唯一键冲突。
     */
    @Test
    @DisplayName("R28 回收后重新授权复用数据范围记录")
    void reactivatesRevokedScopeBinding() {
        IamRole role = role(2L);
        IamRoleDataScope revoked = new IamRoleDataScope();
        revoked.setId(19L);
        revoked.setRoleId(2L);
        revoked.setScopeType("ALL");
        revoked.setStatus("REVOKED");
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(roleDataScopeRepository.findByRoleId(2L)).thenReturn(List.of(revoked));
        when(roleDataScopeRepository.save(any(IamRoleDataScope.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IamRoleDataScope restored = service.bindRoleDataScope(2L, "ALL", null, null, null, 1L);

        assertThat(restored.getId()).isEqualTo(19L);
        assertThat(restored.getStatus()).isEqualTo("ACTIVE");
        assertThat(restored.getEffectiveTime()).isNotNull();
    }

    private IamRole role(Long id) {
        IamRole role = new IamRole();
        role.setId(id);
        role.setRoleType("FUNC");
        role.setStatus("ACTIVE");
        role.setIsDeleted(0);
        return role;
    }
}
