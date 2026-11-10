package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;

public record RefreshTokenRequest(
        @Input(value = Input.Kind.TOKEN) String refreshToken
) {
    @Override public String toString() { return "RefreshTokenRequest[REDACTED]"; }
}

