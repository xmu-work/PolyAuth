package com.polyauth.oop.domain.model;

import java.time.LocalDateTime;

/**
 * 审计日志筛选条件。
 */
public record AuditQuery(
        Long userId,
        String systemCode,
        String logType,
        String username,
        String action,
        Integer success,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
