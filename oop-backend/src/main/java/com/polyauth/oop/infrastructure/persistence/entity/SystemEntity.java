package com.polyauth.oop.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 业务系统只读数据库模型。
 */
@Entity
@Table(name = "iam_system")
public class SystemEntity {

    @Id
    private Long id;

    @Column(name = "system_code", nullable = false, length = 64)
    private String systemCode;

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

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
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
