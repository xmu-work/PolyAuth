package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IamTokenRepository extends JpaRepository<IamToken, String> {
    Optional<IamToken> findByTokenHash(String tokenHash);
    List<IamToken> findByUserId(Long userId);
    List<IamToken> findByUserIdAndStatus(Long userId, String status);
}
