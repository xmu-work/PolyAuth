package com.polyauth.service.ddd.domain.model;

/**
 * 权限节点的不可变判定事实。
 *
 * @param permissionId  权限 ID
 * @param systemCode    所属系统编码
 * @param requiredAction 权限要求的操作为
 * @param active        权限是否启用
 */
public record PermissionFact(Long permissionId, String systemCode,
                             OperationAction requiredAction, boolean active) {
}
