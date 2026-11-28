package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface IamPermissionRepository extends JpaRepository<IamPermission, Long> {
    Optional<IamPermission> findBySystemIdAndPermissionCode(Long systemId, String permissionCode);
    List<IamPermission> findBySystemIdAndIsDeleted(Long systemId, Integer isDeleted);

    /**
     * 数据查看权限可浏览的只读权限：菜单和 action=VIEW 的按钮。
     */
    @Query("SELECT p.id FROM IamPermission p, IamSystem s "
            + "WHERE p.systemId = s.id AND s.systemCode = :systemCode "
            + "AND p.action = 'VIEW' AND p.status = 'ACTIVE' AND p.isDeleted = 0 "
            + "AND s.status = 'ACTIVE' AND s.isDeleted = 0")
    Set<Long> findViewPermissionIds(@Param("systemCode") String systemCode);
}
