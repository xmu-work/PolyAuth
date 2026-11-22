package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import com.polyauth.oop.infrastructure.persistence.mapper.UserAccountMapper;
import com.polyauth.oop.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户账号领域端口的只读关系数据库适配器。
 */
@Repository
public class JpaUserAccountRepositoryAdapter implements UserAccountRepository {

    private final UserAccountJpaRepository repository;

    /**
     * 注入账号 Spring Data Repository。
     */
    public JpaUserAccountRepositoryAdapter(UserAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserAccount> findById(Long userId) {
        return repository.findById(userId).map(UserAccountMapper::toDomain);
    }

    @Override
    public Optional<UserAccount> findByUserNo(String userNo) {
        return repository.findByUserNoAndIsDeleted(userNo, 0)
                .map(UserAccountMapper::toDomain);
    }

    @Override
    public UserAccount save(UserAccount user) {
        throw new UnsupportedOperationException("OOP 切片暂不修改用户账号主数据");
    }
}
