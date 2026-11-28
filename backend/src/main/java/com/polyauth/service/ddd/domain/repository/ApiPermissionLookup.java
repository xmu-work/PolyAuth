package com.polyauth.service.ddd.domain.repository;

/**
 * API 到功能权限的映射事实。
 *
 * @param permissionId 映射权限 ID；未映射时为空
 */
public record ApiPermissionLookup(Long permissionId) {
}
