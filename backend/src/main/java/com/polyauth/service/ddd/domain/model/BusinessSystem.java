package com.polyauth.service.ddd.domain.model;

import com.polyauth.service.ddd.domain.exception.DomainErrorCode;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;

import java.time.LocalDateTime;

/**
 * 接入业务系统聚合根。
 */
public final class BusinessSystem {

    private final Long id;
    private final String systemCode;
    private final String systemName;
    private final String callbackUrl;
    private final String appSecret;
    private final String status;
    private final String description;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;

    private BusinessSystem(Long id, String systemCode, String systemName,
                           String callbackUrl, String appSecret,
                           String status, String description,
                           boolean deleted, LocalDateTime createdAt,
                           LocalDateTime updatedAt, Long version) {
        this.id = id;
        this.systemCode = systemCode;
        this.systemName = systemName;
        this.callbackUrl = callbackUrl;
        this.appSecret = appSecret;
        this.status = status;
        this.description = description;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * 登记启用中的业务系统。
     */
    public static BusinessSystem create(String code, String name, String description,
                                        LocalDateTime now) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new DomainRuleException(
                    DomainErrorCode.PERMISSION_INVALID,
                    "系统编码和名称不能为空");
        }
        return new BusinessSystem(null, code.trim(), name.trim(), null,
                null, "ACTIVE", description, false, now, now, 0L);
    }

    /**
     * 从持久化事实恢复业务系统。
     */
    public static BusinessSystem restore(Long id, String code, String name,
                                         String callbackUrl, String appSecret, String status,
                                         String description, boolean deleted,
                                         LocalDateTime createdAt,
                                         LocalDateTime updatedAt, Long version) {
        return new BusinessSystem(id, code, name, callbackUrl, appSecret, status,
                description, deleted, createdAt, updatedAt, version);
    }

    public boolean isActive() {
        return !deleted && "ACTIVE".equals(status);
    }

    public Long id() {
        return id;
    }

    public String systemCode() {
        return systemCode;
    }

    public String systemName() {
        return systemName;
    }

    public String callbackUrl() {
        return callbackUrl;
    }

    public String appSecret() {
        return appSecret;
    }

    public String status() {
        return status;
    }

    public String description() {
        return description;
    }

    public boolean deleted() {
        return deleted;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
