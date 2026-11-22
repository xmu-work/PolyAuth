package com.polyauth.structured.interfaces.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验 ID 列表中不存在 null、零或负数。
 */
@Documented
@Constraint(validatedBy = PositiveIdsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveIds {

    /**
     * 校验失败提示。
     */
    String message() default "ID 列表必须全部为正整数";

    /**
     * 校验分组。
     */
    Class<?>[] groups() default {};

    /**
     * 校验负载。
     */
    Class<? extends Payload>[] payload() default {};
}
