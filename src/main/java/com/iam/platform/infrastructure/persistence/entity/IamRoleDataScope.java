package com.iam.platform.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-数据范围授权实体。
 *
 * <p>范围类型属于领域属性值，统一使用本类常量。BRANCH/DEPT 必须绑定组织，
 * PROJECT 必须绑定项目，ALL/SELF 不绑定目标。
 */
@Data
@Entity
@Table(name = "iam_role_data_scope")
public class IamRoleDataScope {

    public static final String TYPE_ALL = "ALL";
    public static final String TYPE_BRANCH = "BRANCH";
    public static final String TYPE_DEPT = "DEPT";
    public static final String TYPE_PROJECT = "PROJECT";
    public static final String TYPE_SELF = "SELF";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    /** ACTIVE / REVOKED / EXPIRED。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
