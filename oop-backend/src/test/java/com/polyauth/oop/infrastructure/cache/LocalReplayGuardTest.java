package com.polyauth.oop.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证本地防重放缓存的 TTL 拦截与过期清理行为。
 */
class LocalReplayGuardTest {

    @Test
    @DisplayName("同一接入方和 nonce 在 TTL 内重复使用被拒绝")
    void rejectsDuplicateNonceWithinTtl() {
        LocalReplayGuard guard = new LocalReplayGuard();
        ReflectionTestUtils.setField(guard, "nonceTtlSeconds", 300L);

        assertThat(guard.tryUse("COURSE_APP", "nonce-1")).isTrue();
        assertThat(guard.tryUse("COURSE_APP", "nonce-1")).isFalse();
        assertThat(guard.tryUse("COURSE_APP", "nonce-2")).isTrue();
    }

    @Test
    @DisplayName("TTL 到期后清理旧 nonce 并允许重新使用")
    void purgesExpiredNoncesWithoutDoublingTtl() {
        LocalReplayGuard guard = new LocalReplayGuard();
        ReflectionTestUtils.setField(guard, "nonceTtlSeconds", 0L);

        assertThat(guard.tryUse("COURSE_APP", "nonce-expired")).isTrue();
        assertThat(guard.tryUse("COURSE_APP", "nonce-expired")).isTrue();
    }
}
