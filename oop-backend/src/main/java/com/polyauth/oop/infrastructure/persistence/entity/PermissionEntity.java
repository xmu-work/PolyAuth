package com.polyauth.oop.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 前端权限节点只读数据库模型。
 */
@Entity
@Table(name = "iam_permission")
public class PermissionEntity {

    @Id
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "permission_code", nullable = false, length = 64)
    private String permissionCode;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

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

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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
