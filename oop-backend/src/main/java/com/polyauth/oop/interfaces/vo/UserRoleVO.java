package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.RoleGrant;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 用户角色授权响应视图。
 */
public record UserRoleVO(Long id, Long userId, Long roleId,
                         LocalDateTime effectiveTime, LocalDateTime expireTime,
                         Long grantBy, String grantReason, String status,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 将领域授权转换为 API 视图。
     */
    public static UserRoleVO from(RoleGrant grant) {
        return new UserRoleVO(grant.id(), grant.userId(), grant.roleId(),
                local(grant.effectiveTime()), local(grant.expireTime()),
                grant.grantBy(), grant.reason(), grant.status().name(),
                local(grant.createdAt()), local(grant.updatedAt()));
    }

    private static LocalDateTime local(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZONE);
    }
}
