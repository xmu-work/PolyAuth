package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 令牌会话 Spring Data JPA Repository。
 */
public interface TokenJpaRepository extends JpaRepository<TokenEntity, String> {

    Optional<TokenEntity> findByTokenHash(String tokenHash);
}
