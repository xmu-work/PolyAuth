package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.ApiEndpoint;

import java.time.LocalDateTime;

/**
 * 后端 API 目录响应视图。
 */
public record ApiVO(
        Long id,
        Long systemId,
        Long permissionId,
        String apiCode,
        String apiName,
        String apiPath,
        String httpMethod,
        String status,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ApiVO from(ApiEndpoint endpoint) {
        return new ApiVO(endpoint.id(), endpoint.systemId(),
                endpoint.permissionId(), endpoint.apiCode(), endpoint.apiName(),
                endpoint.apiPath(), endpoint.httpMethod(), endpoint.status(),
                endpoint.deleted() ? 1 : 0, endpoint.createdAt(), endpoint.updatedAt());
    }
}
