package com.polyauth.service.port.identity;

import java.util.List;

/**
 * 统一身份外部审计日志操作人摘要。
 *
 * @param id      操作人稳定用户 ID
 * @param name    操作人姓名
 * @param roles   操作人外部映射角色列表
 * @param deleted 操作人账号当前是否已删除
 */
public record IdentityAuditActorData(
        Long id,
        String name,
        List<String> roles,
        boolean deleted) {

    public IdentityAuditActorData {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
