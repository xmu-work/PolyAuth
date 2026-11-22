package com.polyauth.oop.application.result;

/**
 * 登录成功结果。
 */
public record LoginResult(String accessToken, String tokenType, long expiresIn,
                          String userId, String displayName, String accountStatus) {
}
