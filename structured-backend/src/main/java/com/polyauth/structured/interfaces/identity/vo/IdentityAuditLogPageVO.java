package com.polyauth.structured.interfaces.identity.vo;

import com.polyauth.structured.application.identity.IdentityAuditLogData;
import com.polyauth.structured.application.identity.IdentityPageSlice;

import java.util.List;

/**
 * 统一身份外部审计日志分页响应。
 *
 * @param page     当前页
 * @param pageSize 每页数量
 * @param total    总记录数
 * @param items    当前页日志列表
 */
public record IdentityAuditLogPageVO(
        int page,
        int pageSize,
        long total,
        List<IdentityAuditLogVO> items) {

    /**
     * 将应用层分页切片映射为外部契约响应。
     */
    public static IdentityAuditLogPageVO from(
            int page, int pageSize, IdentityPageSlice<IdentityAuditLogData> slice) {
        return new IdentityAuditLogPageVO(page, pageSize, slice.total(),
                slice.items().stream().map(IdentityAuditLogVO::from).toList());
    }
}
