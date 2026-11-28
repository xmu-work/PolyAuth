package com.polyauth.infrastructure.cache;

import com.polyauth.service.ddd.application.port.IdempotencyStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单副本 OOP 后端的进程内幂等存储。
 */
@Component
public class LocalIdempotencyStore implements IdempotencyStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public synchronized Status acquire(String key, String fingerprint,
                                       long ttlSeconds) {
        Instant now = Instant.now();
        Entry current = entries.get(key);
        if (current != null && current.expiresAt().isBefore(now)) {
            entries.remove(key);
            current = null;
        }
        if (current == null) {
            entries.put(key, new Entry(fingerprint, now.plusSeconds(ttlSeconds),
                    null, false, ttlSeconds));
            return Status.ACQUIRED;
        }
        if (!current.fingerprint().equals(fingerprint)) {
            return Status.CONFLICT;
        }
        return current.completed() ? Status.COMPLETED : Status.IN_PROGRESS;
    }

    @Override
    public synchronized void complete(String key, int status, String response,
                                      long ttlSeconds) {
        Entry current = entries.get(key);
        if (current != null) {
            entries.put(key, new Entry(current.fingerprint(),
                    Instant.now().plusSeconds(ttlSeconds),
                    status + "\n" + response, true, ttlSeconds));
        }
    }

    @Override
    public String getResponse(String key) {
        Entry current = entries.get(key);
        return current == null ? null : current.response();
    }

    @Override
    public synchronized void release(String key) {
        Entry current = entries.get(key);
        if (current != null && !current.completed()) {
            entries.remove(key);
        }
    }

    private record Entry(String fingerprint, Instant expiresAt, String response,
                         boolean completed, long ttlSeconds) {
    }
}
