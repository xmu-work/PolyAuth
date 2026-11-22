package com.polyauth.oop.domain.model;

import java.time.Instant;

/**
 * 已签发令牌的服务端会话事实。
 */
public final class TokenSession {

    private final String tokenId;
    private final Long userId;
    private final String systemCode;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expireAt;
    private final boolean revoked;
    private final Instant revokedAt;
    private final String clientIp;
    private final Instant createdAt;

    private TokenSession(String tokenId, Long userId, String systemCode, String tokenHash,
                         Instant issuedAt, Instant expireAt, boolean revoked,
                         Instant revokedAt, String clientIp, Instant createdAt) {
        this.tokenId = tokenId;
        this.userId = userId;
        this.systemCode = systemCode;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expireAt = expireAt;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.clientIp = clientIp;
        this.createdAt = createdAt;
    }

    /**
     * 创建新的有效令牌会话。
     */
    public static TokenSession issue(String tokenId, Long userId, String systemCode,
                                     String tokenHash, Instant issuedAt, Instant expireAt,
                                     String clientIp) {
        return new TokenSession(tokenId, userId, systemCode, tokenHash, issuedAt, expireAt,
                false, null, clientIp, issuedAt);
    }

    /**
     * 从持久化事实恢复令牌会话。
     */
    public static TokenSession restore(String tokenId, Long userId, String systemCode,
                                       String tokenHash, Instant issuedAt, Instant expireAt,
                                       boolean revoked, Instant revokedAt,
                                       String clientIp, Instant createdAt) {
        return new TokenSession(tokenId, userId, systemCode, tokenHash, issuedAt, expireAt,
                revoked, revokedAt, clientIp, createdAt);
    }

    /**
     * 判断服务端会话在指定时刻是否仍有效。
     */
    public boolean isActive(Instant now) {
        return !revoked && expireAt.isAfter(now);
    }

    /**
     * 返回一个已吊销的令牌会话。
     */
    public TokenSession revoke(Instant now) {
        return new TokenSession(tokenId, userId, systemCode, tokenHash, issuedAt, expireAt,
                true, now, clientIp, createdAt);
    }

    public String tokenId() {
        return tokenId;
    }

    public Long userId() {
        return userId;
    }

    public String systemCode() {
        return systemCode;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expireAt() {
        return expireAt;
    }

    public boolean revoked() {
        return revoked;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String clientIp() {
        return clientIp;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
