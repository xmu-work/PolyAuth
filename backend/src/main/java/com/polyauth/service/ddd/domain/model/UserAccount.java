package com.polyauth.service.ddd.domain.model;

import java.time.LocalDateTime;

/**
 * 用户账号领域对象，承载登录可用状态。
 */
public final class UserAccount {

    private final Long id;
    private final String userNo;
    private final String passwordHash;
    private final String nameEnc;
    private final String nameHash;
    private final String phoneEnc;
    private final String phoneHash;
    private final Long orgId;
    private final String post;
    private final AccountStatus status;
    private final LocalDateTime leaveTime;
    private final LocalDateTime lastLoginAt;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;
    private final Long createdBy;
    private final Long updatedBy;

    private UserAccount(Long id, String userNo, String passwordHash, String nameEnc,
                        String nameHash, String phoneEnc, String phoneHash, Long orgId,
                        String post, AccountStatus status, LocalDateTime leaveTime,
                        LocalDateTime lastLoginAt, boolean deleted, LocalDateTime createdAt,
                        LocalDateTime updatedAt, Long version, Long createdBy, Long updatedBy) {
        this.id = id;
        this.userNo = userNo;
        this.passwordHash = passwordHash;
        this.nameEnc = nameEnc;
        this.nameHash = nameHash;
        this.phoneEnc = phoneEnc;
        this.phoneHash = phoneHash;
        this.orgId = orgId;
        this.post = post;
        this.status = status;
        this.leaveTime = leaveTime;
        this.lastLoginAt = lastLoginAt;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * 从持久化事实恢复用户账号。
     */
    public static UserAccount restore(Long id, String userNo, String passwordHash,
                                      String nameEnc, AccountStatus status, boolean deleted,
                                      LocalDateTime createdAt, LocalDateTime updatedAt,
                                      Long version) {
        return restore(id, userNo, passwordHash, nameEnc, null, null, null, null,
                null, status, null, null, deleted, createdAt, updatedAt, version,
                null, null);
    }

    /**
     * 从完整持久化事实恢复用户账号。
     */
    public static UserAccount restore(Long id, String userNo, String passwordHash,
                                      String nameEnc, String nameHash, String phoneEnc,
                                      String phoneHash, Long orgId, String post,
                                      AccountStatus status, LocalDateTime leaveTime,
                                      LocalDateTime lastLoginAt, boolean deleted,
                                      LocalDateTime createdAt, LocalDateTime updatedAt,
                                      Long version, Long createdBy, Long updatedBy) {
        return new UserAccount(id, userNo, passwordHash, nameEnc, nameHash, phoneEnc,
                phoneHash, orgId, post, status, leaveTime, lastLoginAt, deleted,
                createdAt, updatedAt, version, createdBy, updatedBy);
    }

    /**
     * 创建正常状态的新账号。
     */
    public static UserAccount create(String userNo, String passwordHash, String nameEnc,
                                     String nameHash, String phoneEnc, String phoneHash,
                                     Long orgId, String post, Long operatorId,
                                     LocalDateTime now) {
        return new UserAccount(null, userNo, passwordHash, nameEnc, nameHash,
                phoneEnc, phoneHash, orgId, post, AccountStatus.NORMAL, null, null,
                false, now, now, 0L, operatorId, operatorId);
    }

    /**
     * 变更账号状态，已封存账号不允许再次变更。
     */
    public UserAccount changeStatus(AccountStatus target, Long operatorId,
                                    LocalDateTime now) {
        if (status == AccountStatus.SEALED) {
            throw new com.polyauth.service.ddd.domain.exception.DomainRuleException(
                    com.polyauth.service.ddd.domain.exception.DomainErrorCode.USER_ALREADY_SEALED,
                    "账号已封存");
        }
        LocalDateTime nextLeaveTime = target == AccountStatus.SEALED
                ? now : leaveTime;
        return new UserAccount(id, userNo, passwordHash, nameEnc, nameHash,
                phoneEnc, phoneHash, orgId, post, target, nextLeaveTime, lastLoginAt,
                deleted, createdAt, now, version, createdBy, operatorId);
    }

    /**
     * 调整账号所属组织和岗位。
     */
    public UserAccount transferTo(Long newOrgId, String newPost, Long operatorId,
                                  LocalDateTime now) {
        if (status == AccountStatus.SEALED) {
            throw new com.polyauth.service.ddd.domain.exception.DomainRuleException(
                    com.polyauth.service.ddd.domain.exception.DomainErrorCode.USER_ALREADY_SEALED,
                    "账号已封存");
        }
        return new UserAccount(id, userNo, passwordHash, nameEnc, nameHash,
                phoneEnc, phoneHash, newOrgId, newPost, status, leaveTime,
                lastLoginAt, deleted, createdAt, now, version, createdBy, operatorId);
    }

    /**
     * 更新密码哈希。
     */
    public UserAccount changePassword(String newPasswordHash, Long operatorId,
                                      LocalDateTime now) {
        return new UserAccount(id, userNo, newPasswordHash, nameEnc, nameHash,
                phoneEnc, phoneHash, orgId, post, status, leaveTime, lastLoginAt,
                deleted, createdAt, now, version, createdBy, operatorId);
    }

    /**
     * 记录最近登录时间。
     */
    public UserAccount recordLogin(LocalDateTime now) {
        return new UserAccount(id, userNo, passwordHash, nameEnc, nameHash,
                phoneEnc, phoneHash, orgId, post, status, leaveTime, now,
                deleted, createdAt, now, version, createdBy, updatedBy);
    }

    /**
     * 判断账号能否参与登录和授权判定。
     */
    public boolean canAuthenticate() {
        return !deleted && status == AccountStatus.NORMAL;
    }

    public Long id() {
        return id;
    }

    public String userNo() {
        return userNo;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String nameEnc() {
        return nameEnc;
    }

    public String nameHash() {
        return nameHash;
    }

    public String phoneEnc() {
        return phoneEnc;
    }

    public String phoneHash() {
        return phoneHash;
    }

    public Long orgId() {
        return orgId;
    }

    public String post() {
        return post;
    }

    public AccountStatus status() {
        return status;
    }

    public LocalDateTime leaveTime() {
        return leaveTime;
    }

    public LocalDateTime lastLoginAt() {
        return lastLoginAt;
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

    public Long createdBy() {
        return createdBy;
    }

    public Long updatedBy() {
        return updatedBy;
    }
}
