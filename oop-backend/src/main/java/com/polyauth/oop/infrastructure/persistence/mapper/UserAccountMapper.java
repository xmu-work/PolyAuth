package com.polyauth.oop.infrastructure.persistence.mapper;

import com.polyauth.oop.domain.model.AccountStatus;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.infrastructure.persistence.entity.UserAccountEntity;

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
                entity.getNameEnc(), AccountStatus.valueOf(entity.getAccountStatus()),
                entity.getIsDeleted() != null && entity.getIsDeleted() != 0,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
