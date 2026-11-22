package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;

import java.time.Instant;

/**
 * 用户角色授权聚合，维护有效期与回收状态。
 */
public final class RoleGrant {

    private Long id;
    private final Long userId;
    private final Long roleId;
    private final Instant effectiveTime;
    private final Instant expireTime;
    private final Long grantBy;
    private final String reason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant revokedAt;
    private RoleGrantStatus status;
    private Long version;

    private RoleGrant(Long id, Long userId, Long roleId, Instant effectiveTime,
                      Instant expireTime, Long grantBy, String reason,
                      Instant createdAt, Instant updatedAt, Instant revokedAt,
                      RoleGrantStatus status, Long version) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
        this.effectiveTime = effectiveTime;
        this.expireTime = expireTime;
        this.grantBy = grantBy;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.revokedAt = revokedAt;
        this.status = status;
        this.version = version;
    }

    /**
     * 创建一条有效角色授权。
     */
    public static RoleGrant grant(Long userId, Long roleId, Instant effectiveTime,
                                  Instant expireTime, Long grantBy, String reason,
                                  Instant now) {
        if (userId == null || roleId == null) {
            throw new DomainRuleException(
                    DomainErrorCode.INVALID_GRANT_PERIOD, "用户和角色不能为空");
        }
        Instant start = effectiveTime == null ? now : effectiveTime;
        if (expireTime != null && !expireTime.isAfter(start)) {
            throw new DomainRuleException(
                    DomainErrorCode.INVALID_GRANT_PERIOD, "失效时间必须晚于生效时间");
        }
        return new RoleGrant(null, userId, roleId, start, expireTime, grantBy, reason,
                now, now, null, RoleGrantStatus.ACTIVE, 0L);
    }

    /**
     * 从持久化事实恢复授权聚合。
     */
    public static RoleGrant restore(Long id, Long userId, Long roleId,
                                    Instant effectiveTime, Instant expireTime,
                                    Long grantBy, String reason, Instant createdAt,
                                    Instant updatedAt, Instant revokedAt,
                                    RoleGrantStatus status, Long version) {
        return new RoleGrant(id, userId, roleId, effectiveTime, expireTime, grantBy,
                reason, createdAt, updatedAt, revokedAt, status, version);
    }

    /**
     * 判断授权在目标时刻是否生效。
     */
    public boolean isEffective(Instant now) {
        boolean started = effectiveTime == null || !effectiveTime.isAfter(now);
        boolean notExpired = expireTime == null || expireTime.isAfter(now);
        return status == RoleGrantStatus.ACTIVE && started && notExpired;
    }

    /**
     * 回收角色授权。
     */
    public void revoke(Instant now) {
        if (status == RoleGrantStatus.REVOKED) {
            throw new DomainRuleException(DomainErrorCode.GRANT_REVOKED, "角色授权已经回收");
        }
        this.status = RoleGrantStatus.REVOKED;
        this.revokedAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public Long roleId() {
        return roleId;
    }

    public Instant effectiveTime() {
        return effectiveTime;
    }

    public Instant expireTime() {
        return expireTime;
    }

    public Long grantBy() {
        return grantBy;
    }

    public String reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public RoleGrantStatus status() {
        return status;
    }

    public Long version() {
        return version;
    }
}
