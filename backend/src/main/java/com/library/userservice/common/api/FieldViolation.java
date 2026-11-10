package com.library.userservice.common.api;

import com.library.userservice.common.validation.ValidationCode;
import java.util.Objects;

public record FieldViolation(String field, ValidationCode code, String message) {
    public FieldViolation { Objects.requireNonNull(field); Objects.requireNonNull(code); Objects.requireNonNull(message); }
    public FieldViolation(String field, ValidationCode code) { this(field, code, code.message()); }
}

