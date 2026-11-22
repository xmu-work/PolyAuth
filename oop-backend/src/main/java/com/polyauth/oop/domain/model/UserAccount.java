package com.polyauth.oop.domain.model;

import java.time.LocalDateTime;

/**
 * 用户账号领域对象，承载登录可用状态。
 */
public final class UserAccount {

    private final Long id;
    private final String userNo;
    private final String passwordHash;
    private final String nameEnc;
    private final AccountStatus status;
    private final boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;

    private UserAccount(Long id, String userNo, String passwordHash, String nameEnc,
                        AccountStatus status, boolean deleted, LocalDateTime createdAt,
                        LocalDateTime updatedAt, Long version) {
        this.id = id;
        this.userNo = userNo;
        this.passwordHash = passwordHash;
        this.nameEnc = nameEnc;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * 从持久化事实恢复用户账号。
     */
    public static UserAccount restore(Long id, String userNo, String passwordHash,
                                      String nameEnc, AccountStatus status, boolean deleted,
                                      LocalDateTime createdAt, LocalDateTime updatedAt,
                                      Long version) {
        return new UserAccount(id, userNo, passwordHash, nameEnc, status,
                deleted, createdAt, updatedAt, version);
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

    public AccountStatus status() {
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

    public Long version() {
        return version;
    }
}
