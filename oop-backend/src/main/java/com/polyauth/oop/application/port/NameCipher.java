package com.polyauth.oop.application.port;

/**
 * 姓名密文读取端口。
 */
public interface NameCipher {

    String decrypt(String encrypted);
}
