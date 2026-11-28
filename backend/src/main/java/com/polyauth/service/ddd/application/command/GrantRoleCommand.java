package com.polyauth.service.ddd.application.command;

import java.time.Instant;

/**
 * 授予用户角色用例命令。
 */
public record GrantRoleCommand(Long userId, Long roleId, Instant expireTime,
                               Long grantBy, String reason) {
}
