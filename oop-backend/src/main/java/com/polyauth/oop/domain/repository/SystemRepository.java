package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.BusinessSystem;

import java.util.List;
import java.util.Optional;

/**
 * 业务系统聚合仓储端口。
 */
public interface SystemRepository {

    Optional<BusinessSystem> findById(Long systemId);

    Optional<BusinessSystem> findByCode(String code);

    List<BusinessSystem> findAll();

    BusinessSystem save(BusinessSystem system);
}
