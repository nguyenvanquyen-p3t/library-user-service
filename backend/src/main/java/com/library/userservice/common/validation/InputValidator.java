package com.library.userservice.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InputValidator implements ConstraintValidator<Input, String> {
    private Input rule;
    @Override public void initialize(Input rule) { this.rule = rule; }
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        var code = InputRules.check(value, rule.value(), rule.required());
        if (code == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(code.name()).addConstraintViolation();
        return false;
    }
}

