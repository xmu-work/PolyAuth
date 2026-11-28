package com.polyauth.service.ddd.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 外部业务系统写入统一身份的审计日志领域记录。
 *
 * @param id          日志主键
 * @param systemCode  所属业务系统编码
 * @param actorUserId 操作人本地用户 ID
 * @param actorUserNo 操作人统一身份工号/学号
 * @param requestId   幂等请求标识
 * @param type        外部业务审计事件类型
 * @param objectType  操作对象类型
 * @param objectId    带系统前缀的对象标识
 * @param target      操作对象摘要
 * @param before      变更前快照（可为空）
 * @param after       变更后快照（可为空）
 * @param termId      学期对象标识
 * @param courseId    课程对象标识
 * @param classId     教学班对象标识
 * @param operateTime 操作时间
 */
public record ExternalAuditRecord(
        Long id,
        String systemCode,
        Long actorUserId,
        String actorUserNo,
        String requestId,
        String type,
        String objectType,
        String objectId,
        String target,
        Map<String, Object> before,
        Map<String, Object> after,
        String termId,
        String courseId,
        String classId,
        LocalDateTime operateTime) {
}
