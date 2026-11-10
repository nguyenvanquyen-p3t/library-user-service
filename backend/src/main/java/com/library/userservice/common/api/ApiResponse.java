package com.library.userservice.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, String messageCode, String message, T data) {
    public ApiResponse { Objects.requireNonNull(messageCode); Objects.requireNonNull(message); }
    public static <T> ApiResponse<T> success(SuccessCode code, T data) {
        return new ApiResponse<>(true, code.name(), code.message(), data);
    }
    public static <T> ApiResponse<T> failure(ErrorCode code, T data) {
        return new ApiResponse<>(false, code.name(), code.message(), data);
    }
    @Override public String toString() {
        return "ApiResponse[success=" + success + ", messageCode=" + messageCode + ", data=REDACTED]";
    }
}

