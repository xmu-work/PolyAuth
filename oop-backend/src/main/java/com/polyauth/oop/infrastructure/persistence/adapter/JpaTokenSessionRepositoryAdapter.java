package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.TokenSession;
import com.polyauth.oop.domain.repository.TokenSessionRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.TokenSessionMapper;
import com.polyauth.oop.infrastructure.persistence.repository.TokenJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 令牌会话领域端口的 JPA 适配器。
 */
@Repository
public class JpaTokenSessionRepositoryAdapter implements TokenSessionRepository {

    private final TokenJpaRepository repository;

    /**
     * 注入令牌 Spring Data Repository。
     */
    public JpaTokenSessionRepositoryAdapter(TokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TokenSession> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(TokenSessionMapper::toDomain);
    }

    @Override
    public void save(TokenSession session) {
        repository.save(TokenSessionMapper.toEntity(session));
    }

    @Override
    public void revokeAllForUser(Long userId, Instant now) {
        repository.findByUserIdAndStatus(userId, "ACTIVE").forEach(entity -> {
            entity.setRevoked(1);
            entity.setStatus("REVOKED");
            entity.setRevokedAt(LocalDateTime.ofInstant(
                    now, ZoneId.of("Asia/Shanghai")));
            repository.save(entity);
        });
    }
}
