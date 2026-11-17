package com.iam.platform.application.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.exception.BusinessException;
import com.iam.platform.infrastructure.persistence.entity.IamLog;
import com.iam.platform.infrastructure.persistence.repository.IamLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/**
 * 统一日志服务。
 *
 * <p>普通操作审计和权限变更日志统一写入 {@code iam_log}。权限变更使用独立的
 * {@code logType=PERMISSION_CHANGE}，并保存前后快照；普通请求使用 {@code ACTION}。
 */
@RequiredArgsConstructor
@Service
public class LogService {

    public static final String TYPE_ACTION = "ACTION";
    public static final String TYPE_PERMISSION_CHANGE = "PERMISSION_CHANGE";
    private static final Set<String> LOG_TYPES = Set.of(TYPE_ACTION, TYPE_PERMISSION_CHANGE);
    private static final int MAX_EXPORT_ROWS = 100_000;

    private final IamLogRepository logRepository;
    private final ObjectMapper objectMapper;

    /**
     * 记录普通请求操作日志（异步，不阻塞业务请求）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String username, String systemCode, String action, String target,
                       String detail, String clientIp, boolean success, String errorMsg,
                       String requestId) {
        IamLog entry = new IamLog();
        entry.setLogType(TYPE_ACTION);
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setSystemCode(systemCode);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setDetail(buildDetail(detail, errorMsg));
        entry.setIp(clientIp);
        entry.setSuccess(success ? 1 : 0);
        entry.setRequestId(requestId);
        entry.setOperateTime(LocalDateTime.now());
        logRepository.save(entry);
    }

    /**
     * 记录权限变更日志，保存前后快照。
     */
    @Transactional
    public void recordPermissionChange(Long operatorId, String systemCode, String action,
                                       String target, Object before, Object after,
                                       String clientIp, String requestId) {
        IamLog entry = new IamLog();
        entry.setLogType(TYPE_PERMISSION_CHANGE);
        entry.setUserId(operatorId);
        entry.setSystemCode(systemCode);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setBeforeSnapshot(toJson(before));
        entry.setAfterSnapshot(toJson(after));
        entry.setIp(clientIp);
        entry.setSuccess(1);
        entry.setRequestId(requestId);
        entry.setOperateTime(LocalDateTime.now());
        logRepository.save(entry);
    }

    /**
     * 查询统一日志，可按操作人、系统、日志类型与时间范围筛选。
     */
    public Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return queryLogs(pageable, userId, systemCode, logType,
                null, null, null, startTime, endTime);
    }

    /**
     * 按用户名、动作和成功状态扩展查询审计日志。
     */
    public Page<IamLog> queryLogs(Pageable pageable, Long userId, String systemCode, String logType,
                                  String username, String action, Integer success,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return logRepository.findAll(buildSpecification(
                userId, systemCode, logType, username, action, success, startTime, endTime), pageable);
    }

    /**
     * 导出满足条件的审计日志。
     */
    public List<IamLog> exportLogs(Long userId, String systemCode, String logType,
                                   String username, String action, Integer success,
                                   LocalDateTime startTime, LocalDateTime endTime) {
        Specification<IamLog> specification = buildSpecification(
                userId, systemCode, logType, username, action, success, startTime, endTime);
        Page<IamLog> firstPage = logRepository.findAll(specification,
                PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "operateTime")));
        if (firstPage.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "导出记录超过 " + MAX_EXPORT_ROWS + " 条，请缩小时间范围或增加筛选条件");
        }
        return firstPage.getContent();
    }

    /**
     * 将审计日志转换为防 CSV 公式注入的 CSV 文本。
     */
    public String toCsv(List<IamLog> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("logId,logType,operateTime,userId,username,action,success,target,ip,systemCode,")
                .append("detail,beforeSnapshot,afterSnapshot,decisionId,requestId\r\n");
        for (IamLog entry : logs) {
            csv.append(escapeCsv(entry.getLogId()))
                    .append(',').append(escapeCsv(entry.getLogType()))
                    .append(',').append(escapeCsv(entry.getOperateTime()))
                    .append(',').append(escapeCsv(entry.getUserId()))
                    .append(',').append(escapeCsv(entry.getUsername()))
                    .append(',').append(escapeCsv(entry.getAction()))
                    .append(',').append(escapeCsv(entry.getSuccess()))
                    .append(',').append(escapeCsv(entry.getTarget()))
                    .append(',').append(escapeCsv(entry.getIp()))
                    .append(',').append(escapeCsv(entry.getSystemCode()))
                    .append(',').append(escapeCsv(entry.getDetail()))
                    .append(',').append(escapeCsv(entry.getBeforeSnapshot()))
                    .append(',').append(escapeCsv(entry.getAfterSnapshot()))
                    .append(',').append(escapeCsv(entry.getDecisionId()))
                    .append(',').append(escapeCsv(entry.getRequestId()))
                    .append("\r\n");
        }
        return csv.toString();
    }

    public Page<IamLog> getUserHistory(Long userId, Pageable pageable) {
        return logRepository.findByUserId(userId, pageable);
    }

    private String buildDetail(String detail, String errorMsg) {
        Map<String, String> map = new LinkedHashMap<>();
        if (detail != null && !detail.isBlank()) {
            map.put("detail", detail);
        }
        if (errorMsg != null && !errorMsg.isBlank()) {
            map.put("error", errorMsg);
        }
        return map.isEmpty() ? null : toJson(map);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "日志快照序列化失败");
        }
    }

    /**
     * 构造统一日志筛选条件并校验时间范围与成功标记。
     */
    private Specification<IamLog> buildSpecification(
            Long userId, String systemCode, String logType,
            String username, String action, Integer success,
            LocalDateTime startTime, LocalDateTime endTime) {
        String typeCandidate = normalize(logType);
        final String normalizedType = typeCandidate == null ? null : typeCandidate.toUpperCase(Locale.ROOT);
        if (normalizedType != null && !LOG_TYPES.contains(normalizedType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日志类型必须为 ACTION 或 PERMISSION_CHANGE");
        }
        if (success != null && success != 0 && success != 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "成功状态只能为 0 或 1");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日志开始时间不能晚于结束时间");
        }
        String normalizedSystem = normalize(systemCode);
        String normalizedUsername = normalize(username);
        String normalizedAction = normalize(action);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (normalizedSystem != null) {
                predicates.add(cb.equal(root.get("systemCode"), normalizedSystem));
            }
            if (normalizedType != null) {
                predicates.add(cb.equal(root.get("logType"), normalizedType));
            }
            if (normalizedUsername != null) {
                predicates.add(cb.like(root.get("username"), "%" + normalizedUsername + "%"));
            }
            if (normalizedAction != null) {
                predicates.add(cb.like(root.get("action"), "%" + normalizedAction + "%"));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operateTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operateTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 去除筛选文本两端空白，空字符串视为未指定。
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 对 CSV 单元格进行公式注入防护与 RFC 4180 转义。
     */
    private String escapeCsv(Object value) {
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
