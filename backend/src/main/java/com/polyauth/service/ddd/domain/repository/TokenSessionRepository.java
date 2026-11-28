package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.TokenSession;

import java.time.Instant;
import java.util.Optional;

/**
 * 令牌会话仓储端口。
 */
public interface TokenSessionRepository {

    Optional<TokenSession> findByTokenHash(String tokenHash);

    void save(TokenSession session);

    void revokeAllForUser(Long userId, Instant now);
}
