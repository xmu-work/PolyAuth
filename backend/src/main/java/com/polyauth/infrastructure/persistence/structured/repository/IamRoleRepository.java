package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IamRoleRepository extends JpaRepository<IamRole, Long> {
    Optional<IamRole> findByRoleCode(String roleCode);
    List<IamRole> findByRoleType(String roleType);
    List<IamRole> findByParentId(Long parentId);
}
