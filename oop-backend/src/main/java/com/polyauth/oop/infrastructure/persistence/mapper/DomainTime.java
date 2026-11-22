package com.polyauth.oop.infrastructure.persistence.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 领域瞬时时间与数据库本地时间之间的显式转换。
 */
public final class DomainTime {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private DomainTime() {
    }

    /**
     * 将数据库业务时间转换为领域瞬时时间。
     */
    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }

    /**
     * 将领域瞬时时间转换为数据库业务时间。
     */
    public static LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, BUSINESS_ZONE);
    }
}
