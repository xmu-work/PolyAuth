package com.polyauth.api.identity.vo;

import com.polyauth.service.port.identity.IdentityAuditActorData;

import java.util.List;

/**
 * 统一身份外部审计日志操作人响应。
 *
 * @param id      操作人稳定用户 ID
 * @param name    操作人姓名
 * @param roles   操作人外部角色列表
 * @param deleted 操作人当前是否已删除
 */
public record IdentityAuditActorVO(
        Long id,
        String name,
        List<String> roles,
        boolean deleted) {

    /**
     * 将应用层操作人摘要映射为外部契约响应。
     */
    public static IdentityAuditActorVO from(IdentityAuditActorData data) {
        if (data == null) {
            return null;
        }
        return new IdentityAuditActorVO(data.id(), data.name(), data.roles(), data.deleted());
    }
}
