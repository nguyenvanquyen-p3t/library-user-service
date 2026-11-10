package com.library.userservice.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RevokeOthersResult(long revokedCount) {
    public RevokeOthersResult {
        if (revokedCount < 0) throw new IllegalArgumentException("Số phiên không được âm");
    }
}

