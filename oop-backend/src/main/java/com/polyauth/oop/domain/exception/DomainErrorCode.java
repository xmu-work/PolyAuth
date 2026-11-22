package com.polyauth.oop.domain.exception;

/**
 * 面向对象后端的领域错误分类。
 */
public enum DomainErrorCode {
    INVALID_ROLE,
    ROLE_INHERIT_CYCLE,
    INVALID_GRANT_PERIOD,
    GRANT_REVOKED
}
