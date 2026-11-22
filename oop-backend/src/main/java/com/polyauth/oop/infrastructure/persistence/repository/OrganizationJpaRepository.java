package com.polyauth.oop.infrastructure.persistence.repository;

import com.polyauth.oop.infrastructure.persistence.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织节点 Spring Data JPA Repository。
 */
public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, Long> {

    Optional<OrganizationEntity> findByOrgCode(String orgCode);

    List<OrganizationEntity> findByParentIdIsNullAndIsDeletedOrderBySortNoAscIdAsc(Integer isDeleted);

    List<OrganizationEntity> findByParentIdAndIsDeletedOrderBySortNoAscIdAsc(
            Long parentId, Integer isDeleted);

    List<OrganizationEntity> findByAncestorsStartingWithAndIsDeleted(
            String prefix, Integer isDeleted);
}
