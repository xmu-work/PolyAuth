package com.polyauth.oop.application.port;

import java.util.List;

/**
 * JWT 签发端口。
 */
public interface AccessTokenIssuer {

    /**
     * 签发带目标系统 audience 的访问令牌。
     */
    IssuedToken issue(String userNo, List<String> systems);

    /**
     * 校验签名、签发方和有效期并读取令牌声明。
     */
    AccessTokenClaims read(String token);

    /**
     * 已签发访问令牌。
     *
     * @param value     令牌字符串
     * @param expiresIn 有效秒数
     */
    record IssuedToken(String value, long expiresIn) {
    }

    /**
     * 已校验访问令牌中的非敏感声明。
     *
     * @param userNo  统一身份工号，对应 JWT subject
     * @param systems 目标系统 audience
     */
    record AccessTokenClaims(String userNo, List<String> systems) {
    }
}
