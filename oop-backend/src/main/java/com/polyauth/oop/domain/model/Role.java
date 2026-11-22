package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;

import java.time.LocalDateTime;

/**
 * 角色聚合根，维护角色状态和基础继承关系。
 */
public final class Role {

    private Long id;
    private final String roleCode;
    private String roleName;
    private final RoleType roleType;
    private Long parentId;
    private RoleStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
    private Long createdBy;
    private Long updatedBy;

    private Role(Long id, String roleCode, String roleName, RoleType roleType,
                 Long parentId, RoleStatus status, String description,
                 LocalDateTime createdAt, LocalDateTime updatedAt,
                 Long version, Long createdBy, Long updatedBy) {
        this.id = id;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleType = roleType;
        this.parentId = parentId;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * 创建一个启用状态的领域角色。
     */
    public static Role create(String roleCode, String roleName, RoleType roleType,
                              String description, Long parentId, Long operatorId,
                              LocalDateTime now) {
        requireText(roleCode, "角色编码不能为空");
        requireText(roleName, "角色名称不能为空");
        if (roleType == null) {
            throw new DomainRuleException(DomainErrorCode.INVALID_ROLE, "角色类型不能为空");
        }
        return new Role(
                null,
                roleCode.trim(),
                roleName.trim(),
                roleType,
                parentId,
                RoleStatus.ACTIVE,
                normalize(description),
                now,
                now,
                0L,
                operatorId,
                operatorId);
    }

    /**
     * 从持久化事实恢复角色聚合。
     */
    public static Role restore(Long id, String roleCode, String roleName, RoleType roleType,
                               Long parentId, RoleStatus status, String description,
                               LocalDateTime createdAt, LocalDateTime updatedAt,
                               Long version, Long createdBy, Long updatedBy) {
        return new Role(id, roleCode, roleName, roleType, parentId, status, description,
                createdAt, updatedAt, version, createdBy, updatedBy);
    }

    /**
     * 修改角色基础信息并维护非自身继承约束。
     */
    public void update(String roleName, String description, RoleStatus status,
                       Long parentId, Long operatorId, LocalDateTime now) {
        if (id != null && id.equals(parentId)) {
            throw new DomainRuleException(
                    DomainErrorCode.ROLE_INHERIT_CYCLE, "角色不能继承自身");
        }
        if (roleName != null) {
            requireText(roleName, "角色名称不能为空");
            this.roleName = roleName.trim();
        }
        if (status != null) {
            this.status = status;
        }
        this.parentId = parentId;
        this.description = normalize(description);
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /**
     * 判断角色是否处于启用状态。
     */
    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    public Long id() {
        return id;
    }

    public String roleCode() {
        return roleCode;
    }

    public String roleName() {
        return roleName;
    }

    public RoleType roleType() {
        return roleType;
    }

    public Long parentId() {
        return parentId;
    }

    public RoleStatus status() {
        return status;
    }

    public String description() {
        return description;
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

    public Long createdBy() {
        return createdBy;
    }

    public Long updatedBy() {
        return updatedBy;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleException(DomainErrorCode.INVALID_ROLE, message);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
