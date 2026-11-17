package com.iam.platform.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 角色类型枚举。
 *
 * <p>对应数据库 iam_role.role_type 字段。
 * 支持功能角色与岗位型数据角色；两者共用角色继承和用户授予机制。
 */
@Getter
public enum RoleType {

    FUNC("FUNC", "功能角色"),
    DATA("DATA", "岗位型数据角色");

    private final String code;
    private final String description;

    RoleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static RoleType fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知角色类型: " + code));
    }
}
