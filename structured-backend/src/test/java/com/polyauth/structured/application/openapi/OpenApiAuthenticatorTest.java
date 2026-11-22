package com.polyauth.structured.application.openapi;

import com.polyauth.structured.TestFixtures;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.crypto.CryptoComponent;
import com.polyauth.structured.infrastructure.persistence.entity.IamSystem;
import com.polyauth.structured.infrastructure.persistence.repository.IamSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 开放 API 接入方认证测试。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAuthenticatorTest {

    @Mock
    private IamSystemRepository systemRepository;
    @Mock
    private ReplayGuard replayGuard;

    private final CryptoComponent crypto = TestFixtures.crypto();
    private OpenApiAuthenticator authenticator;

    /**
     * 初始化真实签名实现与测试时间窗。
     */
    @BeforeEach
    void setUp() {
        authenticator = new OpenApiAuthenticator(systemRepository, replayGuard, crypto);
        ReflectionTestUtils.setField(authenticator, "timestampToleranceSeconds", 300L);
    }

    /**
     * 验证停用接入系统即使密钥正确也按 fail-closed 拒绝。
     */
    @Test
    @DisplayName("R35 停用接入系统拒绝开放 API")
    void disabledApplicationFailsClosed() {
        IamSystem system = new IamSystem();
        system.setSystemCode("OA");
        system.setAppSecret("secret");
        system.setStatus("DISABLED");
        system.setIsDeleted(0);
        when(systemRepository.findBySystemCode("OA")).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> authenticator.verify(
                "OA", String.valueOf(System.currentTimeMillis()), "nonce", "signature"))
                .satisfies(error -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.APP_DISABLED.getCode()));
    }
}
