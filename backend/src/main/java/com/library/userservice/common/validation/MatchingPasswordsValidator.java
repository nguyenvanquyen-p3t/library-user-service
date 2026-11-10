package com.library.userservice.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MatchingPasswordsValidator implements ConstraintValidator<MatchingPasswords, PasswordConfirmation> {
    @Override public boolean isValid(PasswordConfirmation value, ConstraintValidatorContext context) {
        if (value == null || value.newPassword() == null || value.confirmPassword() == null
                || value.newPassword().equals(value.confirmPassword())) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("PASSWORD_MISMATCH")
                .addPropertyNode("confirmPassword").addConstraintViolation();
        return false;
    }
}

