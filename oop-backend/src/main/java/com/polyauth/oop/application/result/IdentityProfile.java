package com.polyauth.oop.application.result;

import java.util.List;

/**
 * 统一身份当前用户快照。
 */
public record IdentityProfile(
        String subject,
        List<String> roles,
        List<String> permissions,
        String name,
        String no) {
}
