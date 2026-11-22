package com.polyauth.oop.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 后端 API 目录只读数据库模型。
 */
@Entity
@Table(name = "iam_api")
public class ApiEntity {

    @Id
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "api_code", nullable = false, length = 64)
    private String apiCode;

    @Column(name = "api_path", nullable = false, length = 255)
    private String apiPath;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
