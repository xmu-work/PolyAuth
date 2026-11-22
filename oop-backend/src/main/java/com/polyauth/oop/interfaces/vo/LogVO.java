package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.AuditEntry;

import java.time.LocalDateTime;

/**
 * 统一日志响应视图。
 */
public record LogVO(
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

    public static LogVO from(AuditEntry entry) {
        return new LogVO(entry.logId(), entry.logType(), entry.userId(),
                entry.username(), entry.action(), entry.operateTime(),
                entry.success(), entry.target(), entry.ip(), entry.systemCode(),
                entry.detail(), entry.beforeSnapshot(), entry.afterSnapshot(),
                entry.decisionId(), entry.requestId());
    }
}
