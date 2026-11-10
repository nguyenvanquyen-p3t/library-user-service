package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import com.library.userservice.user.dto.UserProfile;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GoogleRegistrationResult(UserProfile user, TokenPair token) {
    public GoogleRegistrationResult {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(token, "token");
    }
    @Override public String toString() { return "GoogleRegistrationResult[REDACTED]"; }
}

