package com.polyauth.structured.infrastructure.persistence.repository;

import com.polyauth.structured.infrastructure.persistence.entity.IamDataScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据范围字典仓储。
 */
@Repository
public interface IamDataScopeRepository extends JpaRepository<IamDataScope, Long> {

    /**
     * 按编码查询未删除的数据范围字典。
     */
    Optional<IamDataScope> findByScopeCodeAndIsDeleted(String scopeCode, Integer isDeleted);

    /**
     * 查询全部未删除的数据范围字典。
     */
    List<IamDataScope> findByIsDeletedOrderBySortNoAscIdAsc(Integer isDeleted);
}
