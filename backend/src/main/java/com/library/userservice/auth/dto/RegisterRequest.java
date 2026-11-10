package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.Normalization;

public record RegisterRequest(
        @Input(value = Input.Kind.USERNAME) String userName,
        @Input(value = Input.Kind.EMAIL) String email,
        @Input(value = Input.Kind.NEW_PASSWORD) String password,
        @Input(value = Input.Kind.FULL_NAME) String fullName,
        @Input(value = Input.Kind.PHONE, required = false) String phoneNumber,
        @Input(value = Input.Kind.OTP) String verificationCode
) {
    public RegisterRequest {
        userName = Normalization.identifier(userName);
        email = Normalization.identifier(email);
        fullName = Normalization.fullName(fullName);
    }
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
}

