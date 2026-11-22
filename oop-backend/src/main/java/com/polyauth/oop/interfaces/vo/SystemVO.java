package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.domain.model.BusinessSystem;

import java.time.LocalDateTime;

/**
 * 业务系统安全响应视图。
 */
public record SystemVO(
        Long id,
        String systemCode,
        String systemName,
        String callbackUrl,
        String status,
        String description,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SystemVO from(BusinessSystem system) {
        return new SystemVO(system.id(), system.systemCode(), system.systemName(),
                system.callbackUrl(), system.status(), system.description(),
                system.deleted() ? 1 : 0, system.createdAt(), system.updatedAt());
    }
}
