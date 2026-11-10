package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TokenPair(String accessToken, String refreshToken, String tokenType, int expiresIn) {
    public TokenPair {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(tokenType, "tokenType");
        if (accessToken.isEmpty() || refreshToken.isEmpty() || !"Bearer".equals(tokenType) || expiresIn != 3600)
            throw new IllegalArgumentException("Cặp token không đúng hợp đồng");
    }
    public TokenPair(String accessToken, String refreshToken) { this(accessToken, refreshToken, "Bearer", 3600); }
    @Override public String toString() { return "TokenPair[REDACTED]"; }
}

