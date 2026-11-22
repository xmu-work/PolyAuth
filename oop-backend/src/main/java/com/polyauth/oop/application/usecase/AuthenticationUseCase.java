package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.command.LoginCommand;
import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AccessTokenIssuer;
import com.polyauth.oop.application.port.AccessTokenExpiredException;
import com.polyauth.oop.application.port.AuditTrail;
import com.polyauth.oop.application.port.NameCipher;
import com.polyauth.oop.application.port.PasswordVerifier;
import com.polyauth.oop.application.port.TokenSecretHasher;
import com.polyauth.oop.application.result.LoginResult;
import com.polyauth.oop.application.result.AuthenticatedPrincipal;
import com.polyauth.oop.domain.model.AccountStatus;
import com.polyauth.oop.domain.model.TokenSession;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.TokenSessionRepository;
import com.polyauth.oop.domain.repository.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地认证用例，负责登录、令牌会话和审计编排。
 */
@Service
@Transactional
public class AuthenticationUseCase {

    private final UserAccountRepository userRepository;
    private final TokenSessionRepository tokenRepository;
    private final PasswordVerifier passwordVerifier;
    private final NameCipher nameCipher;
    private final TokenSecretHasher tokenSecretHasher;
    private final AccessTokenIssuer tokenIssuer;
    private final AuditTrail auditTrail;
    private final Clock clock;

    /**
     * 注入认证端口和可控时钟。
     */
    public AuthenticationUseCase(UserAccountRepository userRepository,
                                 TokenSessionRepository tokenRepository,
                                 PasswordVerifier passwordVerifier,
                                 NameCipher nameCipher,
                                 TokenSecretHasher tokenSecretHasher,
                                 AccessTokenIssuer tokenIssuer,
                                 AuditTrail auditTrail,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordVerifier = passwordVerifier;
        this.nameCipher = nameCipher;
        this.tokenSecretHasher = tokenSecretHasher;
        this.tokenIssuer = tokenIssuer;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /**
     * 校验账号密码并签发本地令牌。
     */
    public LoginResult login(LoginCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String systemCode = command.systems() == null || command.systems().isEmpty()
                ? null : command.systems().get(0);
        try {
            if (username.isEmpty() || command.password() == null || command.password().isBlank()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID, "账号和密码不能为空");
            }
            if (systemCode == null || systemCode.isBlank()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID, "登录目标系统不能为空");
            }
            if (command.systems().stream().distinct().count() != command.systems().size()) {
                throw new UseCaseException(
                        ApplicationErrorCode.PARAM_INVALID, "登录目标系统不能重复");
            }
            UserAccount user = userRepository.findByUserNo(username)
                    .filter(candidate -> !candidate.deleted())
                    .filter(candidate -> passwordVerifier.verify(
                            command.password(), candidate.passwordHash()))
                    .orElseThrow(() -> new UseCaseException(
                            ApplicationErrorCode.UNAUTHENTICATED, "账号或密码错误"));
            validateAccount(user);

            AccessTokenIssuer.IssuedToken issued =
                    tokenIssuer.issue(user.userNo(), List.copyOf(command.systems()));
            Instant now = clock.instant();
            TokenSession session = TokenSession.issue(
                    UUID.randomUUID().toString(),
                    user.id(),
                    systemCode,
                    tokenSecretHasher.hash(issued.value()),
                    now,
                    now.plusSeconds(issued.expiresIn()),
                    command.clientIp());
            tokenRepository.save(session);
            auditTrail.recordLogin(user.id(), user.userNo(), systemCode, true,
                    null, command.clientIp(), command.requestId());
            return new LoginResult(issued.value(), "Bearer", issued.expiresIn(),
                    String.valueOf(user.id()), nameCipher.decrypt(user.nameEnc()),
                    user.status().name());
        } catch (UseCaseException exception) {
            auditTrail.recordLogin(null, username, systemCode, false,
                    String.valueOf(exception.errorCode().code()),
                    command.clientIp(), command.requestId());
            throw exception;
        }
    }

    /**
     * 校验原始令牌并返回已认证上下文。
     */
    public AuthenticatedPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new UseCaseException(ApplicationErrorCode.UNAUTHENTICATED, "请先登录");
        }
        TokenSession session = tokenRepository.findByTokenHash(tokenSecretHasher.hash(token))
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.TOKEN_INVALID, "令牌无效"));
        if (session.revoked()) {
            throw new UseCaseException(ApplicationErrorCode.TOKEN_REVOKED, "令牌已吊销");
        }
        if (!session.expireAt().isAfter(clock.instant())) {
            throw new UseCaseException(ApplicationErrorCode.TOKEN_EXPIRED, "令牌过期");
        }
        try {
            tokenIssuer.read(token);
        } catch (AccessTokenExpiredException exception) {
            throw new UseCaseException(ApplicationErrorCode.TOKEN_EXPIRED, "令牌过期");
        } catch (RuntimeException exception) {
            throw new UseCaseException(ApplicationErrorCode.TOKEN_INVALID, "令牌无效");
        }
        return new AuthenticatedPrincipal(session.userId(), session.systemCode());
    }

    /**
     * 吊销令牌；不存在时按幂等成功处理。
     */
    public void logout(String token) {
        tokenRepository.findByTokenHash(tokenSecretHasher.hash(token))
                .map(session -> session.revoke(clock.instant()))
                .ifPresent(tokenRepository::save);
    }

    /**
     * 返回令牌是否有效。
     */
    public boolean validate(String token) {
        try {
            authenticate(token);
            return true;
        } catch (UseCaseException exception) {
            return false;
        }
    }

    /**
     * 查询用户工号，仅用于审计快照。
     */
    public String userNo(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.deleted())
                .map(UserAccount::userNo)
                .orElse(null);
    }

    private void validateAccount(UserAccount user) {
        if (user.status() == AccountStatus.SEALED) {
            throw new UseCaseException(ApplicationErrorCode.ACCOUNT_SEALED, "账号已封存");
        }
        if (user.status() != AccountStatus.NORMAL) {
            throw new UseCaseException(ApplicationErrorCode.ACCOUNT_DISABLED, "账号停用或锁定");
        }
    }
}
