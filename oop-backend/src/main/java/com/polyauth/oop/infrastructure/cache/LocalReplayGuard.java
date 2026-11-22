package com.polyauth.oop.infrastructure.cache;

import com.polyauth.oop.application.port.ReplayGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单副本 OOP 后端的进程内 nonce 防重放实现。
 */
@Component
public class LocalReplayGuard implements ReplayGuard {

    private final Map<String, Instant> nonces = new ConcurrentHashMap<>();

    @Value("${open-api.nonce-ttl:300}")
    private long nonceTtlSeconds;

    @Override
    public boolean tryUse(String appId, String nonce) {
        Instant now = Instant.now();
        nonces.entrySet().removeIf(
                entry -> entry.getValue().isBefore(now.minusSeconds(nonceTtlSeconds)));
        String key = appId + ":" + nonce;
        Instant expiresAt = now.plusSeconds(nonceTtlSeconds);
        return nonces.putIfAbsent(key, expiresAt) == null;
    }
}
