package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.UserAccount;

import java.util.Optional;

/**
 * 用户账号聚合仓储端口。
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(Long userId);

    Optional<UserAccount> findByUserNo(String userNo);

    UserAccount save(UserAccount user);
}
