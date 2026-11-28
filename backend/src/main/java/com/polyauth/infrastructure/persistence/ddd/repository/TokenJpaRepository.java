package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 令牌会话 Spring Data JPA Repository。
 */
public interface TokenJpaRepository extends JpaRepository<TokenEntity, String> {

    Optional<TokenEntity> findByTokenHash(String tokenHash);

    List<TokenEntity> findByUserIdAndStatus(Long userId, String status);
}
