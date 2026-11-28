package com.polyauth.service.port.auth;

/**
 * 认证与令牌用例契约。
 */
public interface AuthUseCase {

    /** 管理后台自身系统编码。 */
    String ADMIN_SYSTEM_CODE = "ADMIN";

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    boolean validateToken(String token);

    String getUserNo(Long userId);

    Long authenticate(String token);

    AuthenticatedSession authenticateSession(String token);

    void revokeUserTokens(Long userId);

    void changePassword(Long userId, String oldPassword, String newPassword);
}
