package com.polyauth.oop.domain.repository;

import java.util.Optional;

/**
 * 已登记 API 操作目录只读端口。
 */
public interface OperationCatalogRepository {

    Optional<ApiPermissionLookup> findByRequest(
            String apiPath, String httpMethod);

    Optional<ApiPermissionLookup> findByOperation(
            String systemCode, String operationId);
}
