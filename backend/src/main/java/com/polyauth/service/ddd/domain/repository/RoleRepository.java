package com.polyauth.service.ddd.domain.repository;

import com.polyauth.service.ddd.domain.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * 角色聚合仓储端口。
 */
public interface RoleRepository {

    Optional<Role> findById(Long roleId);

    Optional<Role> findByCode(String roleCode);

    List<Role> findAll();

    Role save(Role role);
}
