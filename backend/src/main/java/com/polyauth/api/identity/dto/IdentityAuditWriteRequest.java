package com.polyauth.api.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 写入统一身份外部审计日志的请求 DTO。
 *
 * @param requestId  业务操作幂等键
 * @param type       审计事件类型
 * @param objectType 操作对象类型
 * @param objectId   带系统前缀的对象标识
 * @param target     操作对象摘要
 * @param before     变更前快照
 * @param after      变更后快照
 */
public record IdentityAuditWriteRequest(
        @NotBlank
        @Size(max = 64)
        String requestId,
        @NotBlank
        @Size(max = 64)
        String type,
        @NotBlank
        @Size(max = 32)
        String objectType,
        @NotBlank
        @Size(max = 128)
        String objectId,
        @NotBlank
        @Size(max = 500)
        String target,
        Map<String, Object> before,
        Map<String, Object> after) {
}
