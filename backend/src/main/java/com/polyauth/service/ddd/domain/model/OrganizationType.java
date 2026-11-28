package com.polyauth.service.ddd.domain.model;

import com.polyauth.service.ddd.domain.exception.DomainErrorCode;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;

/**
 * 组织类型及其固定层级。
 */
public enum OrganizationType {
    HQ(1),
    BRANCH(2),
    DEPT(3),
    PROJECT(4);

    private final int level;

    OrganizationType(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * 按外部编码解析组织类型。
     */
    public static OrganizationType fromCode(String code) {
        try {
            return OrganizationType.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new DomainRuleException(
                    DomainErrorCode.ORGANIZATION_INVALID, "未知组织类型: " + code);
        }
    }
}
