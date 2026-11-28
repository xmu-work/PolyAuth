package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamRoleParent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 角色多继承边仓储。
 */
@Repository
public interface IamRoleParentRepository extends JpaRepository<IamRoleParent, Long> {

    List<IamRoleParent> findByRoleId(Long roleId);

    List<IamRoleParent> findByRoleIdIn(Collection<Long> roleIds);

    void deleteByRoleId(Long roleId);

    void deleteByRoleIdIn(Collection<Long> roleIds);
}
