package com.polyauth.structured.interfaces.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

/**
 * 正整数 ID 列表校验器。
 */
public class PositiveIdsValidator implements ConstraintValidator<PositiveIds, List<Long>> {

    /**
     * 空列表交由非空约束处理，元素必须全部大于零。
     */
    @Override
    public boolean isValid(List<Long> values, ConstraintValidatorContext context) {
        return values == null || values.stream().allMatch(value -> value != null && value > 0);
    }
}
