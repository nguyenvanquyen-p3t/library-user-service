package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.persistence.VerificationCode;
import com.library.userservice.auth.persistence.VerificationCodeRepository;
import com.library.userservice.auth.persistence.VerificationPurpose;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/** Caller giữ khóa email/purpose trong transaction khi cấp hoặc xác minh OTP. */
@Component
class EmailOtpService {
    private static final int MAX_WRONG_ATTEMPTS = 5;
    private static final int MAX_ISSUANCES = 5;
    private static final long COOLDOWN_SECONDS = 60;
    private static final long WINDOW_SECONDS = 3600;

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final VerificationCodeRepository codes;
    private final OtpHash otpHash;
    private final Clock clock;

    EmailOtpService(JdbcTemplate jdbc, VerificationCodeRepository codes, OtpHash otpHash, Clock clock) {
        this.jdbc = jdbc;
        this.codes = codes;
        this.otpHash = otpHash;
        this.clock = clock;
    }

    void requireConfigured() {
        otpHash.requireConfigured();
    }

    void lock(String email, VerificationPurpose purpose) {
        // Khóa advisory bảo vệ cả khi chưa có bản ghi OTP cho email này.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSetExtractor<Void>) result -> null, "library-user-service:otp:" + purpose.name() + ":" + email);
    }

    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    String issue(String email, VerificationPurpose purpose, Instant now) {
        var recent = codes.findFirst5ByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, purpose);
        if (!recent.isEmpty()) {
            Instant nextAllowed = recent.getFirst().getCreatedAt().plusSeconds(COOLDOWN_SECONDS);
            if (recent.size() == MAX_ISSUANCES) {
                Instant windowEnds = recent.getLast().getCreatedAt().plusSeconds(WINDOW_SECONDS);
                if (windowEnds.isAfter(nextAllowed)) nextAllowed = windowEnds;
            }
            if (nextAllowed.isAfter(now)) {
                Duration wait = Duration.between(now, nextAllowed);
                throw new ApplicationException(ErrorCode.OTP_RATE_LIMITED, wait.plusNanos(999_999_999).getSeconds());
            }
        }

        var unexpiredHashes = recent.stream().filter(previous -> now.isBefore(previous.getExpiresAt()))
                .map(VerificationCode::getCodeHash).toList();
        String code;
        String hash;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            hash = otpHash.encode(email, purpose, code);
        } while (unexpiredHashes.contains(hash));
        codes.insertIssuedCode(email, purpose.name(), hash, now, now.plusSeconds(OtpRequestResult.EXPIRES_IN));
        return code;
    }

    VerificationCode verify(String email, VerificationPurpose purpose, String value, Instant now) {
        var code = codes.findFirstByEmailAndPurposeOrderByIdDesc(email, purpose)
                .orElseThrow(() -> new ApplicationException(ErrorCode.OTP_INVALID));
        if (code.getUsedAt() != null || !now.isBefore(code.getExpiresAt()))
            throw new ApplicationException(ErrorCode.OTP_INVALID);
        if (code.getAttemptCount() >= MAX_WRONG_ATTEMPTS)
            throw new ApplicationException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        if (!otpHash.matches(email, purpose, value, code.getCodeHash())) {
            int attempts = code.getAttemptCount() + 1;
            code.setAttemptCount(attempts);
            throw new WrongOtpAttempt(attempts == MAX_WRONG_ATTEMPTS
                    ? ErrorCode.OTP_ATTEMPTS_EXCEEDED : ErrorCode.OTP_INVALID);
        }
        return code;
    }

    // Chỉ lỗi phát sinh sau khi tăng bộ đếm mới được commit cùng kết quả HTTP 400.
    static final class WrongOtpAttempt extends ApplicationException {
        WrongOtpAttempt(ErrorCode code) {
            super(code);
        }
    }
}
