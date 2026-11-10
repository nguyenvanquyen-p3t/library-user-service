package com.library.userservice.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MatchingPasswordsValidator.class)
public @interface MatchingPasswords {
    String message() default "PASSWORD_MISMATCH";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

