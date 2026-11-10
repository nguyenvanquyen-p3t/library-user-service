package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.Normalization;

public record LoginRequest(
        @Input(value = Input.Kind.IDENTIFIER) String identifier,
        @Input(value = Input.Kind.CURRENT_PASSWORD) String password
) {
    public LoginRequest {
        identifier = Normalization.identifier(identifier);
    }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}

