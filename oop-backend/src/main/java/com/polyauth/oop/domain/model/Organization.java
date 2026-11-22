package com.polyauth.oop.domain.model;

import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;

import java.time.LocalDateTime;

/**
 * 组织聚合根，维护层级、祖先路径和启停状态。
 */
public final class Organization {

    private final Long id;
    private final Long parentId;
    private final String ancestors;
    private final String orgCode;
    private final String orgName;
    private final OrganizationType orgType;
    private final Long leaderUserId;
    private final Integer sortNo;
    private final OrganizationStatus status;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;
    private final Long createdBy;
    private final Long updatedBy;

    private Organization(Long id, Long parentId, String ancestors, String orgCode,
                         String orgName, OrganizationType orgType, Long leaderUserId,
                         Integer sortNo, OrganizationStatus status, boolean deleted,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Long version,
                         Long createdBy, Long updatedBy) {
        this.id = id;
        this.parentId = parentId;
        this.ancestors = ancestors;
        this.orgCode = orgCode;
        this.orgName = orgName;
        this.orgType = orgType;
        this.leaderUserId = leaderUserId;
        this.sortNo = sortNo;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * 创建组织节点并校验类型与层级一致。
     */
    public static Organization create(String orgCode, String orgName, OrganizationType type,
                                      Integer orgLevel, Long parentId, String ancestors,
                                      Integer sortNo, Long operatorId, LocalDateTime now) {
        requireText(orgCode, "组织编码不能为空");
        requireText(orgName, "组织名称不能为空");
        if (orgLevel == null || orgLevel != type.level()) {
            throw new DomainRuleException(
                    DomainErrorCode.ORGANIZATION_INVALID,
                    "组织层级与类型不一致: " + type + " 应为 " + type.level());
        }
        return new Organization(null, parentId, ancestors, orgCode.trim(), orgName.trim(),
                type, null, sortNo == null ? 0 : sortNo, OrganizationStatus.ACTIVE,
                false, now, now, 0L, operatorId, operatorId);
    }

    /**
     * 从数据库事实恢复组织节点。
     */
    public static Organization restore(Long id, Long parentId, String ancestors,
                                       String orgCode, String orgName, OrganizationType orgType,
                                       Long leaderUserId, Integer sortNo,
                                       OrganizationStatus status, boolean deleted,
                                       LocalDateTime createdAt, LocalDateTime updatedAt,
                                       Long version, Long createdBy, Long updatedBy) {
        return new Organization(id, parentId, ancestors, orgCode, orgName, orgType,
                leaderUserId, sortNo, status, deleted, createdAt, updatedAt,
                version, createdBy, updatedBy);
    }

    /**
     * 修改基础属性并返回新的组织快照。
     */
    public Organization update(String newName, Integer newSortNo, Long newLeaderUserId,
                               OrganizationStatus newStatus, Long operatorId,
                               LocalDateTime now) {
        requireText(newName == null ? orgName : newName, "组织名称不能为空");
        if (newSortNo != null && newSortNo < 0) {
            throw new DomainRuleException(
                    DomainErrorCode.ORGANIZATION_INVALID, "组织排序号不能为负数");
        }
        return new Organization(id, parentId, ancestors, orgCode,
                newName == null || newName.isBlank() ? orgName : newName.trim(),
                orgType, newLeaderUserId == null ? leaderUserId : newLeaderUserId,
                newSortNo == null ? sortNo : newSortNo,
                newStatus == null ? status : newStatus, deleted, createdAt, now,
                version, createdBy, operatorId);
    }

    /**
     * 将组织移动到新父节点并重算祖先路径。
     */
    public Organization reparent(Organization parent, Long operatorId, LocalDateTime now) {
        if (parent.id().equals(id)) {
            throw new DomainRuleException(
                    DomainErrorCode.ORGANIZATION_INVALID_MOVE, "不能移动到自身组织下");
        }
        if (parent.level() >= level()) {
            throw new DomainRuleException(
                    DomainErrorCode.ORGANIZATION_INVALID_MOVE,
                    "新上级组织的层级必须高于本节点");
        }
        return new Organization(id, parent.id(), parent.selfPath(), orgCode, orgName,
                orgType, leaderUserId, sortNo, status, deleted, createdAt, now,
                version, createdBy, operatorId);
    }

    /**
     * 停用节点，供父组织停用时批量更新下级。
     */
    public Organization disable(Long operatorId, LocalDateTime now) {
        return new Organization(id, parentId, ancestors, orgCode, orgName, orgType,
                leaderUserId, sortNo, OrganizationStatus.DISABLED, deleted,
                createdAt, now, version, createdBy, operatorId);
    }

    /**
     * 替换祖先路径，供移动组织时同步更新后代。
     */
    public Organization withAncestors(String newAncestors, Long operatorId,
                                      LocalDateTime now) {
        return new Organization(id, parentId, newAncestors, orgCode, orgName, orgType,
                leaderUserId, sortNo, status, deleted, createdAt, now,
                version, createdBy, operatorId);
    }

    public boolean contains(Long ancestorId) {
        return ("," + ancestors + ",").contains("," + ancestorId + ",");
    }

    public String selfPath() {
        return ancestors + "," + id;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public Long id() {
        return id;
    }

    public Long parentId() {
        return parentId;
    }

    public String ancestors() {
        return ancestors;
    }

    public String orgCode() {
        return orgCode;
    }

    public String orgName() {
        return orgName;
    }

    public OrganizationType orgType() {
        return orgType;
    }

    public int level() {
        return orgType.level();
    }

    public Long leaderUserId() {
        return leaderUserId;
    }

    public Integer sortNo() {
        return sortNo;
    }

    public OrganizationStatus status() {
        return status;
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
            throw new DomainRuleException(DomainErrorCode.ORGANIZATION_INVALID, message);
        }
    }
}
