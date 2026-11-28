package com.polyauth.api.identity.vo;

import com.polyauth.service.port.identity.IdentityDepartmentUserData;

/**
 * 统一身份部门用户响应项。
 *
 * @param userId         统一身份用户标识
 * @param no             学号或工号
 * @param name           姓名
 * @param departmentId   所属部门
 * @param departmentName 所属部门名称
 */
public record IdentityDepartmentUserVO(
        String userId,
        String no,
        String name,
        Long departmentId,
        String departmentName) {

    /**
     * 将应用层用户摘要映射为外部契约响应。
     */
    public static IdentityDepartmentUserVO from(IdentityDepartmentUserData data) {
        return new IdentityDepartmentUserVO(data.userId(), data.no(), data.name(),
                data.departmentId(), data.departmentName());
    }
}
