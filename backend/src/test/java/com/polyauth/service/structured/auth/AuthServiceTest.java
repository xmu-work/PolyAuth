package com.polyauth.service.structured.auth;

import com.polyauth.service.port.auth.AuthenticatedSession;
import com.polyauth.service.port.auth.LoginResponse;

import com.polyauth.TestFixtures;
import com.polyauth.service.port.auth.LoginRequest;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import com.polyauth.infrastructure.crypto.CryptoComponent;
import com.polyauth.infrastructure.persistence.structured.entity.IamToken;
import com.polyauth.infrastructure.persistence.structured.entity.IamUser;
import com.polyauth.infrastructure.persistence.structured.repository.IamTokenRepository;
import com.polyauth.infrastructure.persistence.structured.repository.IamUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamTokenRepository tokenRepository;
    @Mock
    private LogService logService;

    private final CryptoComponent crypto = TestFixtures.crypto();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, tokenRepository, crypto, logService);
        ReflectionTestUtils.setField(authService, "jwtSecret", "test-secret-key-at-least-256-bits-long-0123456789");
        ReflectionTestUtils.setField(authService, "accessTokenExpire", 7200L);
        ReflectionTestUtils.setField(authService, "issuer", "polyauth");
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setSystems(List.of(AuthService.ADMIN_SYSTEM_CODE));
        return request;
    }

    private static int code(Throwable e) {
        return ((BusinessException) e).getCode();
    }

    /** 登录成功并返回令牌，同时让令牌仓库按哈希找到刚保存的记录 */
    private String loginAndCaptureToken(IamUser user) {
        when(userRepository.findByUserNo(user.getUserNo())).thenReturn(Optional.of(user));
        LoginResponse response = authService.login(request(user.getUserNo(), "pwd"));
        ArgumentCaptor<IamToken> saved = ArgumentCaptor.forClass(IamToken.class);
        verify(tokenRepository).save(saved.capture());
        lenient().when(tokenRepository.findByTokenHash(saved.getValue().getTokenHash())).thenReturn(Optional.of(saved.getValue()));
        return response.getAccessToken();
    }

    @Test
    void loginSucceedsAndIssuesToken() {
        IamUser user = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        when(userRepository.findByUserNo("u7")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(request("u7", "pwd"));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo("7");
        assertThat(response.getDisplayName()).isEqualTo("张三");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(tokenRepository).save(any(IamToken.class));
        verify(logService).record(eq(7L), eq("u7"), eq(AuthService.ADMIN_SYSTEM_CODE),
                eq("LOGIN"), eq("u7"), isNull(), isNull(), eq(true), isNull(), isNull());
    }

    @Test
    void loginRejectsMissingParams() {
        assertThatThrownBy(() -> authService.login(request("", "pwd")))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.PARAM_MISSING.getCode()));
        assertThatThrownBy(() -> authService.login(request("u", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.PARAM_MISSING.getCode()));
    }

    @Test
    @DisplayName("FR-31 登录目标系统不能重复")
    void loginRejectsDuplicateSystems() {
        LoginRequest duplicateSystems = request("u7", "pwd");
        duplicateSystems.setSystems(List.of("course-project-management", "course-project-management"));

        assertThatThrownBy(() -> authService.login(duplicateSystems))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("FR-31 JWT 使用统一身份 subject 并携带目标系统 audience")
    void loginTokenCarriesExternalSubjectAndAudience() {
        IamUser user = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        when(userRepository.findByUserNo("u7")).thenReturn(Optional.of(user));
        LoginRequest request = request("u7", "pwd");
        request.setSystems(List.of("course-project-management"));

        LoginResponse response = authService.login(request);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        "test-secret-key-at-least-256-bits-long-0123456789"
                                .getBytes(StandardCharsets.UTF_8)))
                .requireIssuer("polyauth")
                .build()
                .parseSignedClaims(response.getAccessToken())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("u7");
        assertThat(claims.getAudience()).containsExactly("course-project-management");
        verify(tokenRepository).save(any(IamToken.class));
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUserWithSameError() {
        IamUser user = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        when(userRepository.findByUserNo("u7")).thenReturn(Optional.of(user));
        when(userRepository.findByUserNo("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request("u7", "bad")))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
        assertThatThrownBy(() -> authService.login(request("nobody", "pwd")))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
        verify(tokenRepository, never()).save(any());
        verify(logService).record(isNull(), eq("u7"), eq(AuthService.ADMIN_SYSTEM_CODE),
                eq("LOGIN"), eq("u7"), isNull(), isNull(), eq(false),
                eq(String.valueOf(ErrorCode.UNAUTHENTICATED.getCode())), isNull());
        verify(logService).record(isNull(), eq("nobody"), eq(AuthService.ADMIN_SYSTEM_CODE),
                eq("LOGIN"), eq("nobody"), isNull(), isNull(), eq(false),
                eq(String.valueOf(ErrorCode.UNAUTHENTICATED.getCode())), isNull());
    }

    @Test
    void loginRejectsSealedAndDisabledAccounts() {
        when(userRepository.findByUserNo("u1")).thenReturn(Optional.of(TestFixtures.user(1L, "SEALED", crypto, "pwd")));
        when(userRepository.findByUserNo("u2")).thenReturn(Optional.of(TestFixtures.user(2L, "LOCKED", crypto, "pwd")));

        assertThatThrownBy(() -> authService.login(request("u1", "pwd")))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.ACCOUNT_SEALED.getCode()));
        assertThatThrownBy(() -> authService.login(request("u2", "pwd")))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode()));
    }

    @Test
    void authenticateReturnsUserIdForIssuedToken() {
        String token = loginAndCaptureToken(TestFixtures.user(7L, "NORMAL", crypto, "pwd"));

        assertThat(authService.authenticate(token)).isEqualTo(7L);
        assertThat(authService.validateToken(token)).isTrue();
    }

    @Test
    void authenticateRejectsGarbageAndUnknownTokens() {
        assertThatThrownBy(() -> authService.authenticate("not-a-jwt"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.TOKEN_INVALID.getCode()));
        assertThat(authService.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void authenticateRejectsExpiredToken() {
        ReflectionTestUtils.setField(authService, "accessTokenExpire", -1L);
        String token = loginAndCaptureToken(TestFixtures.user(7L, "NORMAL", crypto, "pwd"));

        assertThatThrownBy(() -> authService.authenticate(token))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.TOKEN_EXPIRED.getCode()));
    }

    @Test
    void logoutRevokesToken() {
        String token = loginAndCaptureToken(TestFixtures.user(7L, "NORMAL", crypto, "pwd"));

        authService.logout(token);

        assertThatThrownBy(() -> authService.authenticate(token))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.TOKEN_REVOKED.getCode()));
    }

    @Test
    void logoutWithUnknownTokenIsNoop() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("unknown");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void revokeUserTokensRevokesAllActiveTokens() {
        IamToken t1 = new IamToken();
        t1.setRevoked(0);
        IamToken t2 = new IamToken();
        t2.setRevoked(0);
        when(tokenRepository.findByUserIdAndStatus(7L, "ACTIVE")).thenReturn(List.of(t1, t2));

        authService.revokeUserTokens(7L);

        assertThat(List.of(t1, t2)).allSatisfy(t -> {
            assertThat(t.getRevoked()).isEqualTo(1);
            assertThat(t.getStatus()).isEqualTo("REVOKED");
        });
    }
}
