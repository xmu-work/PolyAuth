package com.polyauth.oop.application.port;

/**
 * 令牌不可逆摘要端口。
 */
public interface TokenSecretHasher {

    String hash(String value);
}
