package com.polyauth.service.ddd.application.port;

/**
 * 姓名密文读取端口。
 */
public interface NameCipher {

    String decrypt(String encrypted);
}
