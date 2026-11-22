package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.application.port.AccountCrypto;
import com.polyauth.oop.application.port.ReplayGuard;
import com.polyauth.oop.domain.model.BusinessSystem;
import com.polyauth.oop.domain.repository.SystemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开放 API 接入方 HMAC 认证用例。
 */
@Service
public class OpenApiAuthenticator {

    private final SystemRepository systemRepository;
    private final ReplayGuard replayGuard;
    private final AccountCrypto crypto;

    @Value("${open-api.timestamp-tolerance:300}")
    private long timestampToleranceSeconds;

    /**
     * 注入系统目录、防重放和 HMAC 端口。
     */
    public OpenApiAuthenticator(SystemRepository systemRepository,
                                ReplayGuard replayGuard,
                                AccountCrypto crypto) {
        this.systemRepository = systemRepository;
        this.replayGuard = replayGuard;
        this.crypto = crypto;
    }

    /**
     * 校验接入方签名、时间窗和 nonce。
     */
    public void verify(String appId, String timestamp, String nonce,
                       String signature) {
        if (blank(appId) || blank(timestamp) || blank(nonce) || blank(signature)) {
            throw failure(ApplicationErrorCode.SIGNATURE_INVALID,
                    "缺少接入方签名请求头");
        }
        long epochMillis;
        try {
            epochMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw failure(ApplicationErrorCode.SIGNATURE_INVALID,
                    "时间戳格式非法");
        }
        if (Math.abs(System.currentTimeMillis() - epochMillis)
                > timestampToleranceSeconds * 1000) {
            throw failure(ApplicationErrorCode.SIGNATURE_INVALID,
                    "请求时间戳超出允许范围");
        }
        BusinessSystem system = systemRepository.findByCode(appId)
                .filter(BusinessSystem::isActive)
                .filter(item -> !blank(item.appSecret()))
                .orElseThrow(() -> {
                    BusinessSystem candidate = systemRepository.findByCode(appId)
                            .orElse(null);
                    if (candidate != null && !candidate.isActive()) {
                        return failure(ApplicationErrorCode.APP_DISABLED,
                                "接入系统已停用: " + appId);
                    }
                    return failure(ApplicationErrorCode.SIGNATURE_INVALID,
                            "接入系统不存在或未配置密钥: " + appId);
                });
        String expected = crypto.hmac(system.appSecret(),
                appId + "\n" + timestamp + "\n" + nonce);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw failure(ApplicationErrorCode.SIGNATURE_INVALID,
                    "签名校验失败");
        }
        if (!replayGuard.tryUse(appId, nonce)) {
            throw failure(ApplicationErrorCode.REPLAY_DETECTED,
                    "nonce 已被使用");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private UseCaseException failure(ApplicationErrorCode code, String message) {
        return new UseCaseException(code, message);
    }
}
