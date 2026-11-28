package com.polyauth.service.ddd.application.exception;

/**
 * 应用用例失败异常，携带稳定业务错误码。
 */
public class UseCaseException extends RuntimeException {

    private final ApplicationErrorCode errorCode;

    public UseCaseException(ApplicationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationErrorCode errorCode() {
        return errorCode;
    }
}
