package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.RoleExclusion;

import java.util.List;
import java.util.Optional;

/**
 * 角色互斥关系仓储端口。
 */
public interface RoleExclusionRepository {

    Optional<RoleExclusion> findPair(Long firstRoleId, Long secondRoleId);

    List<RoleExclusion> findByRole(Long roleId);

    RoleExclusion save(RoleExclusion exclusion);

    void delete(Long exclusionId);
}
