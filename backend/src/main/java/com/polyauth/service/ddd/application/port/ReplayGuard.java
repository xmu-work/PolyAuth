package com.polyauth.service.ddd.application.port;

/**
 * 开放 API nonce 防重放端口。
 */
public interface ReplayGuard {

    boolean tryUse(String appId, String nonce);
}
