package com.iam.platform.application.identity;

/**
 * 统一身份部门用户摘要，不包含联系方式。
 *
 * @param userId         统一身份用户标识
 * @param no             学号或工号
 * @param name           姓名
 * @param departmentId   所属部门
 * @param departmentName 所属部门名称
 */
public record IdentityDepartmentUserData(
        String userId,
        String no,
        String name,
        Long departmentId,
        String departmentName) {
}
