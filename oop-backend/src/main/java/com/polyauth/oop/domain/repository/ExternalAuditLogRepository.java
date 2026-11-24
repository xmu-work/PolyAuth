package com.polyauth.oop.domain.repository;

import com.polyauth.oop.domain.model.ExternalAuditRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 外部业务系统统一身份审计日志仓储端口。
 */
public interface ExternalAuditLogRepository {

    /**
     * 按业务系统与幂等键查询已落库的外部审计记录。
     */
    Optional<ExternalAuditRecord> findBySystemAndRequestId(String systemCode, String requestId);

    /**
     * 只追加保存外部审计记录。
     */
    ExternalAuditRecord save(ExternalAuditRecord record, String clientIp);

    /**
     * 按系统、操作人与时间窗口倒序读取候选外部审计记录。
     */
    List<ExternalAuditRecord> findMatching(
            String systemCode,
            Long actorUserId,
            LocalDateTime from,
            LocalDateTime to);
}
