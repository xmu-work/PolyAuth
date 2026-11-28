package com.polyauth.service.ddd.application.port;

/**
 * 密码哈希校验端口。
 */
public interface PasswordVerifier {

    boolean verify(String rawPassword, String storedHash);
}
