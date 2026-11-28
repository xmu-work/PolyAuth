package com.polyauth.service.ddd.domain.model;

import com.polyauth.service.ddd.domain.exception.DomainErrorCode;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 后端 API 目录聚合，维护路径、方法和权限映射。
 */
public final class ApiEndpoint {

    private final Long id;
    private final Long systemId;
    private final Long permissionId;
    private final String apiCode;
    private final String apiName;
    private final String apiPath;
    private final String httpMethod;
    private final String status;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private ApiEndpoint(Long id, Long systemId, Long permissionId,
                        String apiCode, String apiName, String apiPath,
                        String httpMethod, String status, boolean deleted,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.systemId = systemId;
        this.permissionId = permissionId;
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.apiPath = apiPath;
        this.httpMethod = httpMethod;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 登记 API，HTTP 方法统一大写。
     */
    public static ApiEndpoint create(Long systemId, String code, String name,
                                     String path, String method,
                                     LocalDateTime now) {
        if (systemId == null || code == null || code.isBlank()
                || name == null || name.isBlank()
                || path == null || path.isBlank()
                || method == null || method.isBlank()) {
            throw new DomainRuleException(
                    DomainErrorCode.PERMISSION_INVALID, "API 字段不完整");
        }
        return new ApiEndpoint(null, systemId, null, code.trim(),
                name.trim(), path.trim(), method.toUpperCase(Locale.ROOT),
                "ACTIVE", false, now, now);
    }

    /**
     * 从持久化事实恢复 API。
     */
    public static ApiEndpoint restore(Long id, Long systemId, Long permissionId,
                                      String code, String name, String path,
                                      String method, String status, boolean deleted,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ApiEndpoint(id, systemId, permissionId, code, name, path,
                method, status, deleted, createdAt, updatedAt);
    }

    /**
     * 将 API 映射到前端权限，返回新的 API 快照。
     */
    public ApiEndpoint mapPermission(Long newPermissionId, LocalDateTime now) {
        return new ApiEndpoint(id, systemId, newPermissionId, apiCode, apiName,
                apiPath, httpMethod, status, deleted, createdAt, now);
    }

    public boolean belongsTo(Long targetSystemId) {
        return systemId.equals(targetSystemId);
    }

    public Long id() {
        return id;
    }

    public Long systemId() {
        return systemId;
    }

    public Long permissionId() {
        return permissionId;
    }

    public String apiCode() {
        return apiCode;
    }

    public String apiName() {
        return apiName;
    }

    public String apiPath() {
        return apiPath;
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String status() {
        return status;
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
}
