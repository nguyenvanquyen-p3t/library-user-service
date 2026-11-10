package com.library.userservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OtpRequestResult(int expiresIn) {
    public OtpRequestResult {
        if (expiresIn != 600) throw new IllegalArgumentException("OTP có hạn 600 giây");
    }
    public OtpRequestResult() { this(600); }
}

