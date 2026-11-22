package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.command.LoginCommand;
import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AccessTokenIssuer;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.application.port.NameCipher;
import com.polyauth.oop.application.port.PasswordVerifier;
import com.polyauth.oop.application.port.TokenSecretHasher;
import com.polyauth.oop.domain.model.AccountStatus;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.TokenSessionRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationUseCaseTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private TokenSessionRepository tokenRepository;
    @Mock
    private PasswordVerifier passwordVerifier;
    @Mock
    private NameCipher nameCipher;
    @Mock
    private TokenSecretHasher tokenSecretHasher;
    @Mock
    private AccessTokenIssuer tokenIssuer;
    @Mock
    private AuditTrail auditTrail;

    private AuthenticationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AuthenticationUseCase(userRepository, tokenRepository, passwordVerifier,
                nameCipher, tokenSecretHasher, tokenIssuer, auditTrail, CLOCK);
    }

    @Test
    void successfulLoginIssuesAndStoresToken() {
        UserAccount user = activeUser();
        when(userRepository.findByUserNo("T1008")).thenReturn(Optional.of(user));
        when(passwordVerifier.verify("Pass1234", "hash")).thenReturn(true);
        when(tokenIssuer.issue("T1008", List.of("COURSE")))
                .thenReturn(new AccessTokenIssuer.IssuedToken("jwt-token", 7200));
        when(tokenSecretHasher.hash("jwt-token")).thenReturn("token-hash");
        when(nameCipher.decrypt("cipher")).thenReturn("孙晨");

        var result = useCase.login(new LoginCommand(
                "T1008", "Pass1234", List.of("COURSE"), "127.0.0.1", "request-1"));

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.displayName()).isEqualTo("孙晨");
        ArgumentCaptor<com.polyauth.oop.domain.model.TokenSession> session =
                ArgumentCaptor.forClass(com.polyauth.oop.domain.model.TokenSession.class);
        verify(tokenRepository).save(session.capture());
        assertThat(session.getValue().tokenHash()).isEqualTo("token-hash");
        assertThat(session.getValue().systemCode()).isEqualTo("COURSE");
        verify(auditTrail).recordLogin(
                any(), anyString(), anyString(), anyBoolean(), any(), anyString(), anyString());
    }

    @Test
    void wrongPasswordUsesSameErrorAsMissingAccount() {
        when(userRepository.findByUserNo("T1008")).thenReturn(Optional.of(activeUser()));
        when(passwordVerifier.verify("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.login(new LoginCommand(
                "T1008", "wrong", List.of("COURSE"), "127.0.0.1", "request-2")))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).errorCode())
                .isEqualTo(ApplicationErrorCode.UNAUTHENTICATED);
    }

    private static UserAccount activeUser() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        return UserAccount.restore(1L, "T1008", "hash", "cipher",
                AccountStatus.NORMAL, false, now, now, 0L);
    }
}
