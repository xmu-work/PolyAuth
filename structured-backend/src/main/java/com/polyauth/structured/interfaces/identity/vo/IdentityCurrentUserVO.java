package com.polyauth.structured.interfaces.identity.vo;

import com.polyauth.structured.application.identity.IdentityProfile;

import java.util.List;

/**
 * 统一身份当前用户、角色与前端权限响应。
 */
public record IdentityCurrentUserVO(
        String subject,
        List<String> roles,
        List<String> permissions,
        String name,
        String no) {

    /**
     * 将应用层身份快照映射为外部契约响应。
     */
    public static IdentityCurrentUserVO from(IdentityProfile profile) {
        return new IdentityCurrentUserVO(profile.subject(), profile.roles(),
                profile.permissions(), profile.name(), profile.no());
    }
}
