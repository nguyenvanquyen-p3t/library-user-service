package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.MatchingPasswords;
import com.library.userservice.common.validation.Normalization;
import com.library.userservice.common.validation.PasswordConfirmation;

@MatchingPasswords
public record ResetPasswordRequest(
        @Input(value = Input.Kind.EMAIL) String email,
        @Input(value = Input.Kind.OTP) String verificationCode,
        @Input(value = Input.Kind.NEW_PASSWORD) String newPassword,
        @Input(value = Input.Kind.NEW_PASSWORD) String confirmPassword
) implements PasswordConfirmation {
    public ResetPasswordRequest {
        email = Normalization.identifier(email);
    }
    @Override public String toString() { return "ResetPasswordRequest[REDACTED]"; }
}

