package com.polyauth.oop.domain.exception;

/**
 * 领域不变量或领域规则被违反时抛出的异常。
 */
public class DomainRuleException extends RuntimeException {

    private final DomainErrorCode errorCode;

    /**
     * 使用稳定领域错误码创建异常。
     */
    public DomainRuleException(DomainErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 返回稳定的领域错误码。
     */
    public DomainErrorCode errorCode() {
        return errorCode;
    }
}
