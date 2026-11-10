package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GoogleRegistrationRequiredResult(String email, String fullName, String suggestedUserName, String registrationToken) {
    public GoogleRegistrationRequiredResult {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(registrationToken, "registrationToken");
    }
    @JsonProperty("isNewUser") public boolean isNewUser() { return true; }
    @JsonProperty("accountLinked") public boolean accountLinked() { return false; }
    @Override public String toString() { return "GoogleRegistrationRequiredResult[REDACTED]"; }
}

