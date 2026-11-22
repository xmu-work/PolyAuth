package com.polyauth.structured.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据范围字典实体。
 *
 * <p>字典只描述范围类型及展示信息；具体角色绑定和授权有效期由
 * {@link IamRoleDataScope} 保存。
 */
@Data
@Entity
@Table(name = "iam_data_scope")
public class IamDataScope {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "scope_code", nullable = false, length = 64)
    private String scopeCode;

    @Column(name = "scope_name", nullable = false, length = 128)
    private String scopeName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "tinyint")
    private Integer isDeleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
