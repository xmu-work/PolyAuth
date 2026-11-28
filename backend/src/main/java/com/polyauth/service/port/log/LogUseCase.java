package com.polyauth.service.port.log;

import com.polyauth.infrastructure.persistence.structured.entity.IamLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一审计日志用例契约。
 */
public interface LogUseCase {

    void record(Long userId, String username, String systemCode, String action, String target,
                String detail, String clientIp, boolean success, String errorMsg, String requestId);

    void recordPermissionChange(Long operatorId, String systemCode, String action,
                                String target, Object before, Object after,
                                String clientIp, String requestId);

    Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                           LocalDateTime startTime, LocalDateTime endTime);

    Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                           String username, String action, Integer success,
                           LocalDateTime startTime, LocalDateTime endTime);

    List<IamLog> exportLogs(Long userId, String systemCode, String logType,
                            String username, String action, Integer success,
                            LocalDateTime startTime, LocalDateTime endTime);

    String toCsv(List<IamLog> logs);

    Page<IamLog> getUserHistory(Long userId, Pageable pageable);
}
