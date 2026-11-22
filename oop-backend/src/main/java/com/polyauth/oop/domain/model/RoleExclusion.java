package com.polyauth.oop.domain.model;

import java.time.LocalDateTime;

/**
 * 两个互斥角色之间的规范化关系，较小 ID 始终在前。
 */
public record RoleExclusion(
        Long id,
        Long roleId,
        Long excludedRoleId,
        Long createdBy,
        LocalDateTime createdAt) {

    /**
     * 创建规范化互斥关系。
     */
    public static RoleExclusion create(Long firstRoleId, Long secondRoleId,
                                       Long operatorId, LocalDateTime now) {
        Long first = Math.min(firstRoleId, secondRoleId);
        Long second = Math.max(firstRoleId, secondRoleId);
        return new RoleExclusion(null, first, second, operatorId, now);
    }

    /**
     * 返回与指定角色相对的另一个角色 ID。
     */
    public Long peerOf(Long targetRoleId) {
        return targetRoleId.equals(roleId) ? excludedRoleId : roleId;
    }
}
