package com.library.userservice.user.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.MatchingPasswords;
import com.library.userservice.common.validation.PasswordConfirmation;

@MatchingPasswords
public record ChangePasswordRequest(
        @Input(value = Input.Kind.CURRENT_PASSWORD) String currentPassword,
        @Input(value = Input.Kind.NEW_PASSWORD) String newPassword,
        @Input(value = Input.Kind.NEW_PASSWORD) String confirmPassword
) implements PasswordConfirmation {
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}

