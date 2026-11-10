package com.library.userservice.common.api;

import java.util.List;

public record ValidationErrorData(List<FieldViolation> errors) {
    public ValidationErrorData {
        errors = List.copyOf(errors);
        if (errors.isEmpty()) throw new IllegalArgumentException("Danh sách lỗi không được rỗng");
    }
}

