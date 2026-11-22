package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OtpRequestResult(int expiresIn) {
    public static final int EXPIRES_IN = 600;

    public OtpRequestResult {
        if (expiresIn != EXPIRES_IN) throw new IllegalArgumentException("OTP có hạn 600 giây");
    }
    public OtpRequestResult() { this(EXPIRES_IN); }
}

