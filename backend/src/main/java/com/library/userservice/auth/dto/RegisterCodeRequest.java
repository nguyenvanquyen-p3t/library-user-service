package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.Normalization;

public record RegisterCodeRequest(
        @Input(value = Input.Kind.USERNAME) String userName,
        @Input(value = Input.Kind.EMAIL) String email
) {
    public RegisterCodeRequest {
        userName = Normalization.identifier(userName);
        email = Normalization.identifier(email);
    }
}

