package com.polyauth.structured.interfaces.identity.vo;

import com.polyauth.structured.application.identity.IdentityAuditLogData;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一身份外部审计日志响应项。
 *
 * @param id       日志主键
 * @param at       操作时间
 * @param actor    操作人
 * @param type     审计事件类型
 * @param target   操作对象摘要
 * @param before   操作前快照
 * @param after    操作后快照
 * @param termId   学期对象标识
 * @param courseId 课程对象标识
 * @param classId  教学班对象标识
 */
public record IdentityAuditLogVO(
        Long id,
        OffsetDateTime at,
        IdentityAuditActorVO actor,
        String type,
        String target,
        Map<String, Object> before,
        Map<String, Object> after,
        String termId,
        String courseId,
        String classId) {

    /**
     * 将应用层日志映射为外部契约响应。
     */
    public static IdentityAuditLogVO from(IdentityAuditLogData data) {
        return new IdentityAuditLogVO(
                data.id(),
                data.at(),
                IdentityAuditActorVO.from(data.actor()),
                data.type(),
                data.target(),
                data.before(),
                data.after(),
                data.termId(),
                data.courseId(),
                data.classId());
    }
}
