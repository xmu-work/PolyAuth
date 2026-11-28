package com.polyauth.service.ddd.application.port;

/**
 * 写请求幂等存储端口。
 */
public interface IdempotencyStore {

    /**
     * 幂等键处理状态。
     */
    enum Status {
        ACQUIRED,
        IN_PROGRESS,
        COMPLETED,
        CONFLICT
    }

    Status acquire(String key, String fingerprint, long ttlSeconds);

    void complete(String key, int status, String response, long ttlSeconds);

    String getResponse(String key);

    void release(String key);
}
