package com.polyauth.service.ddd.adapter.openapi;

import com.polyauth.service.port.openapi.OpenApiUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 开放 API 签名校验用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddOpenApiService implements OpenApiUseCase {

    private final com.polyauth.service.ddd.application.usecase.OpenApiAuthenticator authenticator;

    @Override
    public void verify(String appId, String timestamp, String nonce, String signature) {
        authenticator.verify(appId, timestamp, nonce, signature);
    }
}
