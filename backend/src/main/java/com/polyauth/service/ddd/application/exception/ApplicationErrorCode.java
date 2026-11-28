package com.polyauth.service.ddd.application.exception;

/**
 * 应用层稳定错误码，与两套后端外部契约保持一致。
 */
public enum ApplicationErrorCode {
    PARAM_INVALID(1001, 400),
    IDEMPOTENCY_CONFLICT(1004, 409),
    RESOURCE_NOT_FOUND(1005, 404),
    UNAUTHENTICATED(2001, 401),
    TOKEN_INVALID(2002, 401),
    TOKEN_EXPIRED(2003, 401),
    TOKEN_REVOKED(2004, 401),
    ACCOUNT_SEALED(2005, 403),
    ACCOUNT_DISABLED(2006, 403),
    NO_FUNCTION_PERM(2007, 403),
    SIGNATURE_INVALID(2009, 401),
    REPLAY_DETECTED(2010, 401),
    APP_DISABLED(2011, 403),
    ROLE_INHERIT_CYCLE(3002, 409),
    ORGANIZATION_INVALID_MOVE(3003, 409),
    USER_ALREADY_SEALED(3004, 409),
    GRANT_DUPLICATE(3005, 409),
    ROLE_EXCLUSION_CONFLICT(3011, 409),
    TARGET_USER_ABNORMAL(3010, 409),
    RESOURCE_DUPLICATE(3009, 409);

    private final int code;
    private final int httpStatus;

    ApplicationErrorCode(int code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
