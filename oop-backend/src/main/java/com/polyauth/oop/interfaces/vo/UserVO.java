package com.polyauth.oop.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 用户脱敏响应视图。
 */
public record UserVO(
        Long id,
        String userNo,
        String realName,
        String phoneMasked,
        Long orgId,
        String post,
        String accountStatus,
        LocalDateTime leaveTime,
        LocalDateTime lastLoginAt,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
