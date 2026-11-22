package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.Organization;

import java.util.List;
import java.util.Optional;

/**
 * 组织聚合仓储端口。
 */
public interface OrganizationRepository {

    Optional<Organization> findById(Long orgId);

    Optional<Organization> findByCode(String orgCode);

    List<Organization> findRoots();

    List<Organization> findByParentId(Long parentId);

    List<Organization> findByAncestorsPrefix(String prefix);

    Organization save(Organization organization);

    List<Organization> saveAll(List<Organization> organizations);
}
