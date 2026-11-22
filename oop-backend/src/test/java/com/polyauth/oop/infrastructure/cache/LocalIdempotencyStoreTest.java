package com.polyauth.oop.infrastructure.cache;

import com.polyauth.oop.application.port.IdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证幂等键冲突、完成和重放语义。
 */
class LocalIdempotencyStoreTest {

    @Test
    @DisplayName("FR-41 相同请求完成后可重放响应")
    void replaysCompletedResponse() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();

        assertThat(store.acquire("key-1", "fingerprint", 60))
                .isEqualTo(IdempotencyStore.Status.ACQUIRED);
        store.complete("key-1", 200, "{\"code\":0}", 60);

        assertThat(store.acquire("key-1", "fingerprint", 60))
                .isEqualTo(IdempotencyStore.Status.COMPLETED);
        assertThat(store.getResponse("key-1")).isEqualTo("200\n{\"code\":0}");
    }

    @Test
    @DisplayName("FR-41 同一幂等键不能用于不同请求")
    void rejectsDifferentFingerprint() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        store.acquire("key-1", "first", 60);

        assertThat(store.acquire("key-1", "second", 60))
                .isEqualTo(IdempotencyStore.Status.CONFLICT);
    }
}
