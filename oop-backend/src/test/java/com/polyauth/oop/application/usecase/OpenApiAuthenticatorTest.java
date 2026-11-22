package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AccountCrypto;
import com.polyauth.oop.application.port.ReplayGuard;
import com.polyauth.oop.domain.model.BusinessSystem;
import com.polyauth.oop.domain.repository.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证开放 API HMAC 和时间窗认证。
 */
class OpenApiAuthenticatorTest {

    private final SystemRepository systems = mock(SystemRepository.class);
    private final ReplayGuard replayGuard = mock(ReplayGuard.class);
    private final AccountCrypto crypto = mock(AccountCrypto.class);
    private OpenApiAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new OpenApiAuthenticator(systems, replayGuard, crypto);
        ReflectionTestUtils.setField(authenticator,
                "timestampToleranceSeconds", 300L);
        when(systems.findByCode("COURSE")).thenReturn(Optional.of(
                BusinessSystem.restore(1L, "COURSE", "课程系统", null,
                        "secret", "ACTIVE", null, false,
                        LocalDateTime.now(), LocalDateTime.now(), 0L)));
    }

    @Test
    @DisplayName("FR-35 合法接入方签名通过认证")
    void acceptsValidSignature() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        when(crypto.hmac("secret", "COURSE\n" + timestamp + "\nnonce-1"))
                .thenReturn("signature");
        when(replayGuard.tryUse("COURSE", "nonce-1")).thenReturn(true);

        authenticator.verify("COURSE", timestamp, "nonce-1", "signature");
    }

    @Test
    @DisplayName("FR-35 重复 nonce 返回 2010")
    void rejectsReplayedNonce() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        when(crypto.hmac("secret", "COURSE\n" + timestamp + "\nnonce-1"))
                .thenReturn("signature");
        when(replayGuard.tryUse("COURSE", "nonce-1")).thenReturn(false);

        assertThatThrownBy(() -> authenticator.verify(
                "COURSE", timestamp, "nonce-1", "signature"))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).errorCode())
                .isEqualTo(ApplicationErrorCode.REPLAY_DETECTED);
    }
}
