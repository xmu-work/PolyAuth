package com.polyauth.oop.application.port;

/**
 * 访问令牌签名、签发方或结构无效。
 */
public class AccessTokenInvalidException extends RuntimeException {

    public AccessTokenInvalidException(Throwable cause) {
        super("访问令牌无效", cause);
    }
}
