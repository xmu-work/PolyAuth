package com.polyauth.api.identity.vo;

/**
 * 单权限码校验响应。
 *
 * @param allowed 是否已授权
 */
public record PermissionCheckVO(boolean allowed) {
}
