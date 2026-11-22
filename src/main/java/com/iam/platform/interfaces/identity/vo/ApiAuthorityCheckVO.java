package com.iam.platform.interfaces.identity.vo;

/**
 * API operationId 调用权限响应。
 *
 * @param operationId 目标接口 operationId
 * @param allowed     是否允许调用
 */
public record ApiAuthorityCheckVO(String operationId, boolean allowed) {
}
