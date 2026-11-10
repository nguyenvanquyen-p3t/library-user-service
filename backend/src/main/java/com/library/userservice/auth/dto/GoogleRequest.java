package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;

public record GoogleRequest(
        @Input(value = Input.Kind.TOKEN) String idToken
) {
    @Override public String toString() { return "GoogleRequest[REDACTED]"; }
}

