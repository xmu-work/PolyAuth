package com.polyauth.infrastructure.persistence.ddd.repository;

import com.polyauth.infrastructure.persistence.ddd.entity.UserAccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户账号 Spring Data JPA Repository。
 */
public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long> {

    Optional<UserAccountEntity> findByUserNoAndIsDeleted(String userNo, Integer isDeleted);

    @Query("""
            select user from UserAccountEntity user
            where user.isDeleted = 0
              and (:orgId is null or user.orgId = :orgId)
              and (:accountStatus is null or user.accountStatus = :accountStatus)
            """)
    Page<UserAccountEntity> findPage(
            @Param("orgId")
            Long orgId,
            @Param("accountStatus")
            String accountStatus,
            Pageable pageable);

    List<UserAccountEntity> findByOrgIdAndIsDeleted(Long orgId, Integer isDeleted);
}
