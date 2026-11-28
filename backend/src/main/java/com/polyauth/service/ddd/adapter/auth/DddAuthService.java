package com.polyauth.service.ddd.adapter.auth;

import com.polyauth.api.shared.RequestIdFilter;
import com.polyauth.service.ddd.application.command.LoginCommand;
import com.polyauth.service.ddd.application.result.AuthenticatedPrincipal;
import com.polyauth.service.ddd.application.result.LoginResult;
import com.polyauth.service.ddd.application.usecase.AuthenticationUseCase;
import com.polyauth.service.port.auth.AuthUseCase;
import com.polyauth.service.port.auth.AuthenticatedSession;
import com.polyauth.service.port.auth.LoginRequest;
import com.polyauth.service.port.auth.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 认证与令牌用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddAuthService implements AuthUseCase {

    private final AuthenticationUseCase useCase;

    @Override
    public LoginResponse login(LoginRequest request) {
        LoginResult result = useCase.login(new LoginCommand(
                request.getUsername(), request.getPassword(), request.getSystems(),
                request.getClientIp(), RequestIdFilter.currentRequestId()));
        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.accessToken());
        response.setTokenType(result.tokenType());
        response.setExpiresIn(result.expiresIn());
        response.setUserId(result.userId());
        response.setDisplayName(result.displayName());
        response.setAccountStatus(result.accountStatus());
        return response;
    }

    @Override
    public void logout(String accessToken) {
        useCase.logout(accessToken);
    }

    @Override
    public boolean validateToken(String token) {
        return useCase.validate(token);
    }

    @Override
    public String getUserNo(Long userId) {
        return useCase.userNo(userId);
    }

    @Override
    public Long authenticate(String token) {
        return useCase.authenticate(token).userId();
    }

    @Override
    public AuthenticatedSession authenticateSession(String token) {
        AuthenticatedPrincipal principal = useCase.authenticate(token);
        return new AuthenticatedSession(principal.userId(), principal.systemCode());
    }

    @Override
    public void revokeUserTokens(Long userId) {
        throw new UnsupportedOperationException("DDD 未提供按用户批量吊销令牌的独立用例");
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        useCase.changePassword(userId, oldPassword, newPassword);
    }
}
