package com.library.userservice.auth.dto;

import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.Normalization;

public record GoogleRegisterRequest(
        @Input(value = Input.Kind.TOKEN) String registrationToken,
        @Input(value = Input.Kind.USERNAME) String userName,
        @Input(value = Input.Kind.FULL_NAME) String fullName,
        @Input(value = Input.Kind.PHONE, required = false) String phoneNumber
) {
    public GoogleRegisterRequest {
        userName = Normalization.identifier(userName);
        fullName = Normalization.fullName(fullName);
    }
    @Override public String toString() { return "GoogleRegisterRequest[REDACTED]"; }
}

