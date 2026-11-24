package com.polyauth.oop.application.result;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一身份外部审计日志条目应用结果。
 *
 * @param id       日志主键
 * @param at       操作时间
 * @param actor    操作人
 * @param type     审计事件类型
 * @param target   操作对象摘要
 * @param before   变更前快照
 * @param after    变更后快照
 * @param termId   学期对象标识
 * @param courseId 课程对象标识
 * @param classId  教学班对象标识
 */
public record IdentityAuditLog(
        Long id,
        OffsetDateTime at,
        IdentityAuditActor actor,
        String type,
        String target,
        Map<String, Object> before,
        Map<String, Object> after,
        String termId,
        String courseId,
        String classId) {

    public IdentityAuditLog {
        before = before == null ? Map.of() : before;
        after = after == null ? Map.of() : after;
    }
}
