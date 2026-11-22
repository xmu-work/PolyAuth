package com.polyauth.oop.application.port;

/**
 * 账号敏感字段加解密、密码哈希、检索哈希和展示脱敏端口。
 */
public interface AccountCrypto {

    String encrypt(String plaintext);

    String decrypt(String encrypted);

    String hash(String value);

    String hmac(String secret, String value);

    String hashPassword(String password);

    String maskName(String name);

    String maskPhone(String phone);
}
