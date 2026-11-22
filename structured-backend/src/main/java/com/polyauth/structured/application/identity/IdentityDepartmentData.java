package com.polyauth.structured.application.identity;

/**
 * 统一身份部门摘要。
 *
 * @param id          部门标识
 * @param code        部门编码
 * @param name        部门名称
 * @param parentId    上级部门
 * @param hasChildren 是否存在直接下级
 */
public record IdentityDepartmentData(
        Long id,
        String code,
        String name,
        Long parentId,
        boolean hasChildren) {
}
