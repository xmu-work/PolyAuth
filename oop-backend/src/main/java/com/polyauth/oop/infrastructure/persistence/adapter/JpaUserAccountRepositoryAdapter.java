package com.polyauth.oop.infrastructure.persistence.adapter;

import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.domain.model.DomainPage;
import com.polyauth.oop.domain.repository.UserAccountRepository;
import com.polyauth.oop.infrastructure.persistence.entity.UserAccountEntity;
import com.polyauth.oop.infrastructure.persistence.mapper.UserAccountMapper;
import com.polyauth.oop.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
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
        return UserAccountMapper.toDomain(
                repository.save(UserAccountMapper.toEntity(user)));
    }

    @Override
    public DomainPage<UserAccount> findPage(int page, int pageSize, Long orgId,
                                            String accountStatus) {
        Page<UserAccountEntity> result = repository.findPage(
                orgId, accountStatus, PageRequest.of(page - 1, pageSize));
        return new DomainPage<>(result.getTotalElements(), page, pageSize,
                result.getContent().stream()
                        .map(UserAccountMapper::toDomain)
                        .toList());
    }

    @Override
    public List<UserAccount> findByOrgId(Long orgId) {
        return repository.findByOrgIdAndIsDeleted(orgId, 0).stream()
                .map(UserAccountMapper::toDomain)
                .toList();
    }
}
