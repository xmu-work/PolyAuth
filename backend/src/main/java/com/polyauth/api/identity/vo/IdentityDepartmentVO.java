package com.polyauth.api.identity.vo;

import com.polyauth.service.port.identity.IdentityDepartmentData;

/**
 * 统一身份部门响应项。
 *
 * @param id          部门标识
 * @param code        部门编码
 * @param name        部门名称
 * @param parentId    上级部门
 * @param hasChildren 是否存在直接下级
 */
public record IdentityDepartmentVO(
        Long id,
        String code,
        String name,
        Long parentId,
        boolean hasChildren) {

    /**
     * 将应用层部门摘要映射为外部契约响应。
     */
    public static IdentityDepartmentVO from(IdentityDepartmentData data) {
        return new IdentityDepartmentVO(data.id(), data.code(), data.name(),
                data.parentId(), data.hasChildren());
    }
}
