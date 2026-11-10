package com.library.userservice.common.exception;

import com.library.userservice.common.api.ErrorCode;
import java.util.Objects;

/** Chỉ mang mã công khai; không nhận message/giá trị xác thực tùy ý. */
public class ApplicationException extends RuntimeException {
    private final ErrorCode code;
    private final Long retryAfterSeconds;

    public ApplicationException(ErrorCode code) { this(code, null); }
    public ApplicationException(ErrorCode code, Long retryAfterSeconds) {
        super(Objects.requireNonNull(code).message());
        if (code == ErrorCode.OTP_RATE_LIMITED && (retryAfterSeconds == null || retryAfterSeconds < 0))
            throw new IllegalArgumentException("Lỗi giới hạn OTP cần thời gian được thử lại");
        if (code != ErrorCode.OTP_RATE_LIMITED && retryAfterSeconds != null)
            throw new IllegalArgumentException("Retry-After chỉ dùng cho giới hạn OTP");
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public ErrorCode code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
}
