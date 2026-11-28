package com.polyauth.api.identity.vo;

import com.polyauth.service.port.identity.IdentityUserData;

/**
 * 统一身份用户响应。
 *
 * @param userId 统一身份用户标识
 * @param no     学号或工号
 * @param name   姓名
 */
public record IdentityUserVO(String userId, String no, String name) {

    /**
     * 将应用层用户摘要映射为外部契约响应。
     */
    public static IdentityUserVO from(IdentityUserData data) {
        return new IdentityUserVO(data.userId(), data.no(), data.name());
    }
}
