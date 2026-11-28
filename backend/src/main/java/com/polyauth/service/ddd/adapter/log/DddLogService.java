package com.polyauth.service.ddd.adapter.log;

import com.polyauth.infrastructure.persistence.structured.entity.IamLog;
import com.polyauth.service.ddd.application.port.AuditTrail;
import com.polyauth.service.ddd.application.usecase.AuditQueryUseCase;
import com.polyauth.service.ddd.domain.model.AuditEntry;
import com.polyauth.service.ddd.domain.model.AuditQuery;
import com.polyauth.service.ddd.domain.model.DomainPage;
import com.polyauth.service.port.log.LogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一审计日志用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddLogService implements LogUseCase {

    private final AuditQueryUseCase queryUseCase;
    private final AuditTrail auditTrail;

    @Override
    public void record(Long userId, String username, String systemCode, String action, String target,
                       String detail, String clientIp, boolean success, String errorMsg, String requestId) {
        auditTrail.recordRequest(userId, username, systemCode, action, target,
                clientIp, success, errorMsg, requestId);
    }

    @Override
    public void recordPermissionChange(Long operatorId, String systemCode, String action,
                                       String target, Object before, Object after,
                                       String clientIp, String requestId) {
        auditTrail.recordPermissionChange(operatorId, systemCode, action, target, before, after);
    }

    @Override
    public Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return query(pageable, new AuditQuery(userId, systemCode, logType,
                null, null, null, startTime, endTime));
    }

    @Override
    public Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                                  String username, String action, Integer success,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return query(pageable, new AuditQuery(userId, systemCode, logType,
                username, action, success, startTime, endTime));
    }

    @Override
    public List<IamLog> exportLogs(Long userId, String systemCode, String logType,
                                   String username, String action, Integer success,
                                   LocalDateTime startTime, LocalDateTime endTime) {
        return queryUseCase.export(new AuditQuery(userId, systemCode, logType,
                        username, action, success, startTime, endTime))
                .stream().map(this::toEntity).toList();
    }

    @Override
    public String toCsv(List<IamLog> logs) {
        return queryUseCase.csv(logs.stream().map(this::toDomain).toList());
    }

    @Override
    public Page<IamLog> getUserHistory(Long userId, Pageable pageable) {
        return query(pageable, new AuditQuery(userId, null, null,
                null, null, null, null, null));
    }

    private Page<IamLog> query(Pageable pageable, AuditQuery query) {
        DomainPage<AuditEntry> page = queryUseCase.page(query,
                pageable.getPageNumber() + 1, pageable.getPageSize());
        return new PageImpl<>(page.items().stream().map(this::toEntity).toList(),
                pageable, page.total());
    }

    private IamLog toEntity(AuditEntry entry) {
        IamLog log = new IamLog();
        log.setLogId(entry.logId());
        log.setLogType(entry.logType());
        log.setUserId(entry.userId());
        log.setUsername(entry.username());
        log.setAction(entry.action());
        log.setOperateTime(entry.operateTime());
        log.setSuccess(entry.success());
        log.setTarget(entry.target());
        log.setIp(entry.ip());
        log.setSystemCode(entry.systemCode());
        log.setDetail(entry.detail());
        log.setBeforeSnapshot(entry.beforeSnapshot());
        log.setAfterSnapshot(entry.afterSnapshot());
        log.setDecisionId(entry.decisionId());
        log.setRequestId(entry.requestId());
        return log;
    }

    private AuditEntry toDomain(IamLog log) {
        return new AuditEntry(log.getLogId(), log.getLogType(), log.getUserId(),
                log.getUsername(), log.getAction(), log.getOperateTime(),
                log.getSuccess(), log.getTarget(), log.getIp(), log.getSystemCode(),
                log.getDetail(), log.getBeforeSnapshot(), log.getAfterSnapshot(),
                log.getDecisionId(), log.getRequestId());
    }
}
