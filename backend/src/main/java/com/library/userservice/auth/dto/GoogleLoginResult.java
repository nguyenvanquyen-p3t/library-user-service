package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GoogleLoginResult(String accessToken, String refreshToken, String tokenType, int expiresIn) {
    public GoogleLoginResult {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(tokenType, "tokenType");
        if (accessToken.isEmpty() || refreshToken.isEmpty() || !"Bearer".equals(tokenType) || expiresIn != 3600)
            throw new IllegalArgumentException("Cặp token không đúng hợp đồng");
    }
    public GoogleLoginResult(TokenPair token) { this(token.accessToken(), token.refreshToken(), token.tokenType(), token.expiresIn()); }
    @JsonProperty("isNewUser") public boolean isNewUser() { return false; }
    @JsonProperty("accountLinked") public boolean accountLinked() { return false; }
    @Override public String toString() { return "GoogleLoginResult[REDACTED]"; }
}

