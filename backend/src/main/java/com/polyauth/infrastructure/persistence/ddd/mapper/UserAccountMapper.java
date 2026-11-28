package com.polyauth.infrastructure.persistence.ddd.mapper;

import com.polyauth.service.ddd.domain.model.AccountStatus;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.infrastructure.persistence.ddd.entity.UserAccountEntity;

/**
 * 用户账号数据库模型与领域对象映射器。
 */
public final class UserAccountMapper {

    private UserAccountMapper() {
    }

    /**
     * 将账号数据库模型转换为领域对象。
     */
    public static UserAccount toDomain(UserAccountEntity entity) {
        return UserAccount.restore(entity.getId(), entity.getUserNo(), entity.getPasswordHash(),
                entity.getNameEnc(), entity.getNameHash(), entity.getPhoneEnc(),
                entity.getPhoneHash(), entity.getOrgId(), entity.getPost(),
                AccountStatus.valueOf(entity.getAccountStatus()), entity.getLeaveTime(),
                entity.getLastLoginAt(),
                entity.getIsDeleted() != null && entity.getIsDeleted() != 0,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion(),
                entity.getCreatedBy(), entity.getUpdatedBy());
    }

    /**
     * 将领域账号转换为数据库模型。
     */
    public static UserAccountEntity toEntity(UserAccount user) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(user.id());
        entity.setUserNo(user.userNo());
        entity.setPasswordHash(user.passwordHash());
        entity.setNameEnc(user.nameEnc());
        entity.setNameHash(user.nameHash());
        entity.setPhoneEnc(user.phoneEnc());
        entity.setPhoneHash(user.phoneHash());
        entity.setOrgId(user.orgId());
        entity.setPost(user.post());
        entity.setAccountStatus(user.status().name());
        entity.setLeaveTime(user.leaveTime());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setIsDeleted(user.deleted() ? 1 : 0);
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        entity.setVersion(user.version());
        entity.setCreatedBy(user.createdBy());
        entity.setUpdatedBy(user.updatedBy());
        return entity;
    }
}
