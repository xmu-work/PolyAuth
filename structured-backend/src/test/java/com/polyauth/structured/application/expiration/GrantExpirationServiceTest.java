package com.polyauth.structured.application.expiration;

import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.infrastructure.persistence.entity.IamRoleDataScope;
import com.polyauth.structured.infrastructure.persistence.entity.IamUserRole;
import com.polyauth.structured.infrastructure.persistence.repository.IamRoleDataScopeRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 临时授权到期回收测试。
 */
@ExtendWith(MockitoExtension.class)
class GrantExpirationServiceTest {

    @Mock
    private IamUserRoleRepository userRoleRepository;
    @Mock
    private IamRoleDataScopeRepository roleDataScopeRepository;
    @Mock
    private LogService logService;
    @InjectMocks
    private GrantExpirationService service;

    /**
     * 验证用户角色和数据范围到期后写回 EXPIRED 并保存审计快照。
     */
    @Test
    @DisplayName("R25 临时授权到期自动回收")
    void expiresUserRoleAndDataScope() {
        IamUserRole userRole = new IamUserRole();
        userRole.setUserId(1L);
        userRole.setRoleId(2L);
        userRole.setStatus("ACTIVE");
        IamRoleDataScope dataScope = new IamRoleDataScope();
        dataScope.setRoleId(2L);
        dataScope.setScopeType("DEPT");
        dataScope.setOrgId(8L);
        dataScope.setStatus("ACTIVE");
        when(userRoleRepository.findByStatusAndExpireTimeBefore(
                any(String.class), any(LocalDateTime.class))).thenReturn(List.of(userRole));
        when(roleDataScopeRepository.findByStatusAndExpireTimeBefore(
                any(String.class), any(LocalDateTime.class))).thenReturn(List.of(dataScope));

        ExpirationResult result = service.expireDueGrants();

        assertThat(result).isEqualTo(new ExpirationResult(1, 1));
        assertThat(userRole.getStatus()).isEqualTo("EXPIRED");
        assertThat(dataScope.getStatus()).isEqualTo("EXPIRED");
        verify(userRoleRepository).saveAll(List.of(userRole));
        verify(roleDataScopeRepository).saveAll(List.of(dataScope));
        verify(logService, times(2)).recordPermissionChange(
                any(), any(String.class), any(String.class), any(String.class),
                any(), any(), any(), any());
    }
}
