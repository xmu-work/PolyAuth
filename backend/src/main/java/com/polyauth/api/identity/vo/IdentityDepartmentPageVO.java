package com.polyauth.api.identity.vo;

import com.polyauth.service.port.identity.IdentityDepartmentData;
import com.polyauth.service.port.identity.IdentityPageSlice;

import java.util.List;

/**
 * 统一身份部门分页响应。
 *
 * @param page     当前页
 * @param pageSize 每页数量
 * @param total    总记录数
 * @param items    当前页部门
 */
public record IdentityDepartmentPageVO(
        int page,
        int pageSize,
        long total,
        List<IdentityDepartmentVO> items) {

    /**
     * 将应用层分页切片映射为外部契约响应。
     */
    public static IdentityDepartmentPageVO from(int page, int pageSize,
                                                IdentityPageSlice<IdentityDepartmentData> slice) {
        return new IdentityDepartmentPageVO(page, pageSize, slice.total(),
                slice.items().stream().map(IdentityDepartmentVO::from).toList());
    }
}
