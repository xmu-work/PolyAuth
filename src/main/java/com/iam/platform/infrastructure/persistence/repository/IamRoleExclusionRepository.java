package com.iam.platform.infrastructure.persistence.repository;

import com.iam.platform.infrastructure.persistence.entity.IamRoleExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色互斥关系仓储。
 */
@Repository
public interface IamRoleExclusionRepository extends JpaRepository<IamRoleExclusion, Long> {

    /**
     * 查询包含指定角色的全部互斥关系。
     */
    List<IamRoleExclusion> findByRoleIdOrExcludedRoleId(Long roleId, Long excludedRoleId);

    /**
     * 查询一对规范化角色 ID 的互斥关系。
     */
    Optional<IamRoleExclusion> findByRoleIdAndExcludedRoleId(Long roleId, Long excludedRoleId);
}
