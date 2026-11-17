package com.iam.platform.infrastructure.persistence.repository;

import com.iam.platform.infrastructure.persistence.entity.IamRoleDataScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 角色-数据范围授权仓储。
 */
@Repository
public interface IamRoleDataScopeRepository extends JpaRepository<IamRoleDataScope, Long> {

    /**
     * 查询指定角色的全部数据范围授权。
     */
    List<IamRoleDataScope> findByRoleId(Long roleId);

    /**
     * 查询多个角色的数据范围授权，用于按角色继承链合并范围。
     */
    List<IamRoleDataScope> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * 查询指定状态下已经到期的数据范围授权。
     */
    List<IamRoleDataScope> findByStatusAndExpireTimeBefore(String status, LocalDateTime now);
}
