package com.polyauth.structured.interfaces.identity.vo;

import com.polyauth.structured.application.identity.IdentityDepartmentUserData;
import com.polyauth.structured.application.identity.IdentityPageSlice;

import java.util.List;

/**
 * 统一身份部门用户分页响应。
 *
 * @param page     当前页
 * @param pageSize 每页数量
 * @param total    总记录数
 * @param items    当前页用户
 */
public record IdentityDepartmentUserPageVO(
        int page,
        int pageSize,
        long total,
        List<IdentityDepartmentUserVO> items) {

    /**
     * 将应用层分页切片映射为外部契约响应。
     */
    public static IdentityDepartmentUserPageVO from(
            int page, int pageSize,
            IdentityPageSlice<IdentityDepartmentUserData> slice) {
        return new IdentityDepartmentUserPageVO(page, pageSize, slice.total(),
                slice.items().stream().map(IdentityDepartmentUserVO::from).toList());
    }
}
