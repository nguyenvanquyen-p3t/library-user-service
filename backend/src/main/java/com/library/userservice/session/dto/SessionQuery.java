package com.library.userservice.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SessionQuery(
        @NotNull(message = "REQUIRED") @Min(value = 0, message = "OUT_OF_RANGE") Integer page,
        @NotNull(message = "REQUIRED") @Min(value = 1, message = "OUT_OF_RANGE")
        @Max(value = 100, message = "OUT_OF_RANGE") Integer size) {
    public SessionQuery() { this(0, 20); }
}

