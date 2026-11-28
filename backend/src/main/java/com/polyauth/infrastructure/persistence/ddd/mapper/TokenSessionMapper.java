package com.polyauth.infrastructure.persistence.ddd.mapper;

import com.polyauth.service.ddd.domain.model.TokenSession;
import com.polyauth.infrastructure.persistence.ddd.entity.TokenEntity;

/**
 * 令牌会话领域对象与数据库模型映射器。
 */
public final class TokenSessionMapper {

    private TokenSessionMapper() {
    }

    /**
     * 将令牌数据库模型转换为领域对象。
     */
    public static TokenSession toDomain(TokenEntity entity) {
        return TokenSession.restore(entity.getTokenId(), entity.getUserId(),
                entity.getSystemCode(), entity.getTokenHash(),
                DomainTime.toInstant(entity.getIssuedAt()),
                DomainTime.toInstant(entity.getExpireAt()),
                entity.getRevoked() != null && entity.getRevoked() == 1,
                DomainTime.toInstant(entity.getRevokedAt()), entity.getClientIp(),
                DomainTime.toInstant(entity.getCreatedAt()));
    }

    /**
     * 将令牌领域对象转换为数据库模型。
     */
    public static TokenEntity toEntity(TokenSession session) {
        TokenEntity entity = new TokenEntity();
        entity.setTokenId(session.tokenId());
        entity.setUserId(session.userId());
        entity.setSystemCode(session.systemCode());
        entity.setTokenHash(session.tokenHash());
        entity.setIssuedAt(DomainTime.toLocalDateTime(session.issuedAt()));
        entity.setExpireAt(DomainTime.toLocalDateTime(session.expireAt()));
        entity.setRevoked(session.revoked() ? 1 : 0);
        entity.setRevokedAt(DomainTime.toLocalDateTime(session.revokedAt()));
        entity.setStatus(session.revoked() ? "REVOKED" : "ACTIVE");
        entity.setClientIp(session.clientIp());
        entity.setCreatedAt(DomainTime.toLocalDateTime(session.createdAt()));
        return entity;
    }
}
