package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.TokenSession;
import com.polyauth.oop.domain.repository.TokenSessionRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.TokenSessionMapper;
import com.polyauth.oop.infrastructure.persistence.repository.TokenJpaRepository;
import org.springframework.stereotype.Repository;

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
}
