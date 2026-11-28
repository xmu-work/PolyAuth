package com.polyauth.service.ddd.application.port;

/**
 * 访问令牌签名有效但已经过期。
 */
public class AccessTokenExpiredException extends RuntimeException {

    public AccessTokenExpiredException() {
        super("访问令牌已过期");
    }
}
