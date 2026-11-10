package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.Normalization;

public record ForgotPasswordCodeRequest(
        @Input(value = Input.Kind.EMAIL) String email
) {
    public ForgotPasswordCodeRequest {
        email = Normalization.identifier(email);
    }
}

