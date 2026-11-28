package com.polyauth.service.port.openapi;

/**
 * 开放 API 签名校验用例契约。
 */
public interface OpenApiUseCase {

    void verify(String appId, String timestamp, String nonce, String signature);
}
