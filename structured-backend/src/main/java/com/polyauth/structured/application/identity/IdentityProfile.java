package com.polyauth.structured.application.identity;

import java.util.List;

/**
 * 统一身份当前用户快照。
 *
 * @param subject     统一身份用户标识
 * @param roles       课程系统可见的角色编码
 * @param permissions 目标系统的前端权限码
 * @param name        姓名
 * @param no          学号或工号
 */
public record IdentityProfile(
        String subject,
        List<String> roles,
        List<String> permissions,
        String name,
        String no) {
}
