package com.polyauth.service.ddd.application.result;

/**
 * 统一身份部门用户摘要。
 */
public record IdentityDepartmentUser(
        String userId,
        String no,
        String name,
        Long departmentId,
        String departmentName) {
}
