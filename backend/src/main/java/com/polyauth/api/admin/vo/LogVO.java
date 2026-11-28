package com.polyauth.api.admin.vo;

import com.polyauth.infrastructure.persistence.structured.entity.IamLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一审计日志 API 响应视图。
 */
@Data
public class LogVO {

    private Long logId;
    private String logType;
    private Long userId;
    private String username;
    private String action;
    private LocalDateTime operateTime;
    private Integer success;
    private String target;
    private String ip;
    private String systemCode;
    private String detail;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String decisionId;
    private String requestId;

    /**
     * 将日志实体转换为 VO。
     */
    public static LogVO from(IamLog log) {
        LogVO vo = new LogVO();
        vo.setLogId(log.getLogId());
        vo.setLogType(log.getLogType());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setAction(log.getAction());
        vo.setOperateTime(log.getOperateTime());
        vo.setSuccess(log.getSuccess());
        vo.setTarget(log.getTarget());
        vo.setIp(log.getIp());
        vo.setSystemCode(log.getSystemCode());
        vo.setDetail(log.getDetail());
        vo.setBeforeSnapshot(log.getBeforeSnapshot());
        vo.setAfterSnapshot(log.getAfterSnapshot());
        vo.setDecisionId(log.getDecisionId());
        vo.setRequestId(log.getRequestId());
        return vo;
    }
}
