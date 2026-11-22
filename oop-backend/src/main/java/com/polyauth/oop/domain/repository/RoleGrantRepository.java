package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.RoleGrant;

import java.util.List;
import java.util.Optional;

/**
 * 用户角色授权仓储端口。
 */
public interface RoleGrantRepository {

    Optional<RoleGrant> findByUserIdAndRoleId(Long userId, Long roleId);

    List<RoleGrant> findByUserId(Long userId);

    List<RoleGrant> findByRoleId(Long roleId);

    RoleGrant save(RoleGrant grant);
}
