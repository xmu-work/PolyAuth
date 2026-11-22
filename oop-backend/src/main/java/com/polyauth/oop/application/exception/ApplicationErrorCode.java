package com.polyauth.oop.application.exception;

/**
 * 应用层稳定错误码，与两套后端外部契约保持一致。
 */
public enum ApplicationErrorCode {
    PARAM_INVALID(1001, 400),
    RESOURCE_NOT_FOUND(1005, 404),
    UNAUTHENTICATED(2001, 401),
    TOKEN_INVALID(2002, 401),
    TOKEN_EXPIRED(2003, 401),
    TOKEN_REVOKED(2004, 401),
    ACCOUNT_SEALED(2005, 403),
    ACCOUNT_DISABLED(2006, 403),
    NO_FUNCTION_PERM(2007, 403),
    ROLE_INHERIT_CYCLE(3002, 409),
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
