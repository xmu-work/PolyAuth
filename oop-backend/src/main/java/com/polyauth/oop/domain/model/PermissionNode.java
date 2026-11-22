package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;

import java.time.LocalDateTime;

/**
 * 前端菜单或按钮权限节点。
 */
public final class PermissionNode {

    private final Long id;
    private final Long systemId;
    private final Long parentId;
    private final String permissionCode;
    private final String permissionName;
    private final String permissionType;
    private final String routePath;
    private final OperationAction action;
    private final Integer sortNo;
    private final String status;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private PermissionNode(Long id, Long systemId, Long parentId,
                           String permissionCode, String permissionName,
                           String permissionType, String routePath,
                           OperationAction action, Integer sortNo, String status,
                           boolean deleted, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
        this.id = id;
        this.systemId = systemId;
        this.parentId = parentId;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.permissionType = permissionType;
        this.routePath = routePath;
        this.action = action;
        this.sortNo = sortNo;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建菜单或按钮权限。
     */
    public static PermissionNode create(Long systemId, Long parentId,
                                        String code, String name, String type,
                                        String routePath, OperationAction action,
                                        Integer sortNo, LocalDateTime now) {
        if (systemId == null || code == null || code.isBlank()
                || name == null || name.isBlank()) {
            throw new DomainRuleException(
                    DomainErrorCode.PERMISSION_INVALID, "权限字段不完整");
        }
        if (!"MENU".equals(type) && !"BUTTON".equals(type)) {
            throw new DomainRuleException(
                    DomainErrorCode.PERMISSION_INVALID,
                    "权限类型必须为 MENU 或 BUTTON");
        }
        return new PermissionNode(null, systemId, parentId, code.trim(),
                name.trim(), type, routePath, action,
                sortNo == null ? 0 : sortNo, "ACTIVE", false, now, now);
    }

    /**
     * 从持久化事实恢复权限节点。
     */
    public static PermissionNode restore(Long id, Long systemId, Long parentId,
                                         String code, String name, String type,
                                         String routePath, OperationAction action,
                                         Integer sortNo, String status,
                                         boolean deleted, LocalDateTime createdAt,
                                         LocalDateTime updatedAt) {
        return new PermissionNode(id, systemId, parentId, code, name, type,
                routePath, action, sortNo, status, deleted, createdAt, updatedAt);
    }

    public boolean isActive() {
        return !deleted && "ACTIVE".equals(status);
    }

    public Long id() {
        return id;
    }

    public Long systemId() {
        return systemId;
    }

    public Long parentId() {
        return parentId;
    }

    public String permissionCode() {
        return permissionCode;
    }

    public String permissionName() {
        return permissionName;
    }

    public String permissionType() {
        return permissionType;
    }

    public String routePath() {
        return routePath;
    }

    public OperationAction action() {
        return action;
    }

    public Integer sortNo() {
        return sortNo;
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
