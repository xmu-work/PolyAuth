package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface IamUserRoleRepository extends JpaRepository<IamUserRole, Long> {
    List<IamUserRole> findByUserId(Long userId);
    List<IamUserRole> findByRoleId(Long roleId);
    List<IamUserRole> findByUserIdAndStatus(Long userId, String status);
    Optional<IamUserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 查询同时拥有两个有效角色的用户，用于互斥关系配置前的一致性校验。
     */
    @Query("SELECT DISTINCT currentGrant.userId FROM IamUserRole currentGrant, "
            + "IamUserRole peerGrant "
            + "WHERE currentGrant.userId = peerGrant.userId "
            + "AND currentGrant.roleId = :roleId AND peerGrant.roleId = :excludedRoleId "
            + "AND currentGrant.status = 'ACTIVE' AND peerGrant.status = 'ACTIVE'")
    List<Long> findUserIdsWithBothActiveRoles(
            @Param("roleId")
            Long roleId,
            @Param("excludedRoleId")
            Long excludedRoleId);

    /**
     * 查询指定状态下已经到期的用户角色授权。
     */
    List<IamUserRole> findByStatusAndExpireTimeBefore(String status, LocalDateTime now);
}
