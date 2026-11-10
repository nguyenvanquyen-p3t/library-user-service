package com.library.userservice.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InputValidator.class)
public @interface Input {
    Kind value();
    boolean required() default true;
    String message() default "INVALID_VALUE";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    enum Kind { USERNAME, EMAIL, IDENTIFIER, FULL_NAME, PHONE, NEW_PASSWORD, CURRENT_PASSWORD, TOKEN, OTP }
}

