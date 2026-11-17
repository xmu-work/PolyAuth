package com.iam.platform.application.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.infrastructure.persistence.entity.IamLog;
import com.iam.platform.infrastructure.persistence.repository.IamLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private IamLogRepository logRepository;
    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = new LogService(logRepository, new ObjectMapper());
    }

    @Test
    void actionAndPermissionChangeUseSameTableButDifferentTypes() {
        logService.record(1L, "admin", "ADMIN", "POST /roles", "/roles",
                "roleCode=HR", "127.0.0.1", true, null, "req-1");
        logService.recordPermissionChange(1L, "ADMIN", "GRANT_ROLE", "user:2/role:3",
                null, Map.of("roleId", 3, "status", "ACTIVE"), "127.0.0.1", "req-2");

        ArgumentCaptor<IamLog> logs = ArgumentCaptor.forClass(IamLog.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(logs.capture());
        assertThat(logs.getAllValues().get(0).getLogType()).isEqualTo(LogService.TYPE_ACTION);
        assertThat(logs.getAllValues().get(1).getLogType()).isEqualTo(LogService.TYPE_PERMISSION_CHANGE);
        assertThat(logs.getAllValues().get(1).getBeforeSnapshot()).isNull();
        assertThat(logs.getAllValues().get(1).getAfterSnapshot()).contains("\"roleId\":3");
        assertThat(logs.getAllValues().get(1).getRequestId()).isEqualTo("req-2");
    }

    /**
     * 验证日志查询拒绝起止时间倒置的请求。
     */
    @Test
    @DisplayName("R45 日志时间范围校验")
    void rejectsInvalidTimeRange() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.minusMinutes(1);

        assertThatThrownBy(() -> logService.queryLogs(
                org.springframework.data.domain.PageRequest.of(0, 20),
                null, null, null, start, end))
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }

    /**
     * 验证 CSV 对逗号、双引号和公式前缀进行安全转义。
     */
    @Test
    @DisplayName("R46 CSV 导出安全转义")
    void csvExportEscapesValuesAndFormulaInjection() {
        IamLog log = new IamLog();
        log.setLogId(1L);
        log.setLogType(LogService.TYPE_ACTION);
        log.setAction("=cmd,calc");
        log.setUsername("张\"三");
        log.setSuccess(1);
        log.setOperateTime(LocalDateTime.now());

        String csv = logService.toCsv(List.of(log));

        assertThat(csv).startsWith("logId,logType");
        assertThat(csv).contains("\"'=cmd,calc\"");
        assertThat(csv).contains("\"张\"\"三\"");
    }
}
