package com.polyauth.service.ddd.domain.model;

import java.time.LocalDateTime;

/**
 * 统一审计日志只读领域事实。
 */
public record AuditEntry(
        Long logId,
        String logType,
        Long userId,
        String username,
        String action,
        LocalDateTime operateTime,
        Integer success,
        String target,
        String ip,
        String systemCode,
        String detail,
        String beforeSnapshot,
        String afterSnapshot,
        String decisionId,
        String requestId) {
}
