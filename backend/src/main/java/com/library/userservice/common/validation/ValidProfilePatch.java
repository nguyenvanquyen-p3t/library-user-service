package com.library.userservice.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProfilePatchValidator.class)
public @interface ValidProfilePatch {
    String message() default "EMPTY_PATCH";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

