package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleGrantTest {

    @Test
    void grantIsEffectiveAtStartAndBeforeExpiry() {
        Instant start = Instant.parse("2026-11-22T12:00:00Z");
        Instant expiry = Instant.parse("2026-11-22T13:00:00Z");
        RoleGrant grant = RoleGrant.grant(1L, 2L, start, expiry, 9L, "临时授权", start);

        assertThat(grant.isEffective(start)).isTrue();
        assertThat(grant.isEffective(expiry.minusSeconds(1))).isTrue();
        assertThat(grant.isEffective(expiry)).isFalse();
    }

    @Test
    void expiryMustBeAfterEffectiveTime() {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");

        assertThatThrownBy(() -> RoleGrant.grant(1L, 2L, now, now, 9L, null, now))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    void revokedGrantIsNeverEffective() {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        RoleGrant grant = RoleGrant.grant(1L, 2L, now.minusSeconds(60), null, 9L, null, now);

        grant.revoke(now);

        assertThat(grant.isEffective(now.plusSeconds(1))).isFalse();
    }
}
