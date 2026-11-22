package com.polyauth.oop.application.usecase;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.domain.model.AuditEntry;
import com.polyauth.oop.domain.model.AuditQuery;
import com.polyauth.oop.domain.model.DomainPage;
import com.polyauth.oop.domain.repository.AuditQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 审计日志查询和 CSV 导出用例。
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryUseCase {

    private static final Set<String> LOG_TYPES =
            Set.of("ACTION", "PERMISSION_CHANGE");
    private static final int MAX_EXPORT_ROWS = 100_000;

    private final AuditQueryRepository repository;

    /**
     * 注入审计查询仓储端口。
     */
    public AuditQueryUseCase(AuditQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询审计日志。
     */
    public DomainPage<AuditEntry> page(AuditQuery query, int page, int pageSize) {
        AuditQuery normalized = validate(query);
        return repository.findPage(normalized, page, pageSize);
    }

    /**
     * 按筛选条件导出日志。
     */
    public List<AuditEntry> export(AuditQuery query) {
        AuditQuery normalized = validate(query);
        List<AuditEntry> result = repository.findForExport(
                normalized, MAX_EXPORT_ROWS);
        if (result.size() > MAX_EXPORT_ROWS) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "导出记录超过 " + MAX_EXPORT_ROWS + " 条，请缩小时间范围");
        }
        return result;
    }

    /**
     * 将日志转换为防公式注入的 CSV。
     */
    public String csv(List<AuditEntry> entries) {
        StringBuilder result = new StringBuilder();
        result.append("logId,logType,operateTime,userId,username,action,success,target,")
                .append("ip,systemCode,detail,beforeSnapshot,afterSnapshot,")
                .append("decisionId,requestId\r\n");
        for (AuditEntry entry : entries) {
            result.append(escape(entry.logId()))
                    .append(',').append(escape(entry.logType()))
                    .append(',').append(escape(entry.operateTime()))
                    .append(',').append(escape(entry.userId()))
                    .append(',').append(escape(entry.username()))
                    .append(',').append(escape(entry.action()))
                    .append(',').append(escape(entry.success()))
                    .append(',').append(escape(entry.target()))
                    .append(',').append(escape(entry.ip()))
                    .append(',').append(escape(entry.systemCode()))
                    .append(',').append(escape(entry.detail()))
                    .append(',').append(escape(entry.beforeSnapshot()))
                    .append(',').append(escape(entry.afterSnapshot()))
                    .append(',').append(escape(entry.decisionId()))
                    .append(',').append(escape(entry.requestId()))
                    .append("\r\n");
        }
        return result.toString();
    }

    private AuditQuery validate(AuditQuery query) {
        String type = normalize(query.logType());
        type = type == null ? null : type.toUpperCase(Locale.ROOT);
        if (type != null && !LOG_TYPES.contains(type)) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "日志类型必须为 ACTION 或 PERMISSION_CHANGE");
        }
        if (query.success() != null && query.success() != 0
                && query.success() != 1) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "成功状态只能为 0 或 1");
        }
        if (query.startTime() != null && query.endTime() != null
                && query.startTime().isAfter(query.endTime())) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID,
                    "日志开始时间不能晚于结束时间");
        }
        return new AuditQuery(query.userId(), normalize(query.systemCode()),
                type, normalize(query.username()), normalize(query.action()),
                query.success(), query.startTime(), query.endTime());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escape(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
