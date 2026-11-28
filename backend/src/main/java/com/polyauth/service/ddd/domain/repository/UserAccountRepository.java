package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.model.DomainPage;

import java.util.List;
import java.util.Optional;

/**
 * 用户账号聚合仓储端口。
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(Long userId);

    Optional<UserAccount> findByUserNo(String userNo);

    DomainPage<UserAccount> findPage(int page, int pageSize, Long orgId,
                                     String accountStatus);

    List<UserAccount> findByOrgId(Long orgId);

    UserAccount save(UserAccount user);
}
