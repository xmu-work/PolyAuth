package com.polyauth.service.ddd.application.result;

/**
 * 统一身份部门摘要。
 */
public record IdentityDepartment(
        Long id,
        String code,
        String name,
        Long parentId,
        boolean hasChildren) {
}
