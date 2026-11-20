package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.REGISTER;

import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.dto.RegisterRequest;
import com.library.userservice.auth.persistence.VerificationCodeRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserRole;
import com.library.userservice.user.persistence.UserRoleId;
import com.library.userservice.user.persistence.UserRoleRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationTransactions {
    private static final int MAX_WRONG_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final VerificationCodeRepository codes;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final OtpHash otpHash;
    private final PasswordService passwords;
    private final Clock clock;

    public RegistrationTransactions(JdbcTemplate jdbc, VerificationCodeRepository codes, UserRepository users,
            RoleRepository roles, UserRoleRepository userRoles, OtpHash otpHash, PasswordService passwords, Clock clock) {
        this.jdbc = jdbc;
        this.codes = codes;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.otpHash = otpHash;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issue(RegisterCodeRequest request) {
        otpHash.requireConfigured();
        Instant now = lockEmail(request.email());
        if (users.findByUserName(request.userName()).isPresent())
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS);
        if (users.findByEmail(request.email()).isPresent())
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);

        var recent = codes.findFirst5ByEmailAndPurposeOrderByCreatedAtDescIdDesc(request.email(), REGISTER);
        if (!recent.isEmpty()) {
            Instant nextAllowed = recent.getFirst().getCreatedAt().plusSeconds(60);
            if (recent.size() == 5) {
                Instant windowEnds = recent.getLast().getCreatedAt().plusSeconds(3600);
                if (windowEnds.isAfter(nextAllowed)) nextAllowed = windowEnds;
            }
            if (nextAllowed.isAfter(now)) {
                Duration wait = Duration.between(now, nextAllowed);
                throw new ApplicationException(ErrorCode.OTP_RATE_LIMITED, wait.plusNanos(999_999_999).getSeconds());
            }
        }

        String code;
        String hash;
        var unexpiredHashes = recent.stream().filter(previous -> now.isBefore(previous.getExpiresAt()))
                .map(previous -> previous.getCodeHash()).toList();
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            hash = otpHash.encode(request.email(), REGISTER, code);
        } while (unexpiredHashes.contains(hash));
        codes.insertIssuedCode(request.email(), REGISTER.name(), hash, now, now.plusSeconds(600));
        return code;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = WrongOtpAttempt.class)
    public UserProfile register(RegisterRequest request) {
        Instant now = lockEmail(request.email());
        var code = codes.findFirstByEmailAndPurposeOrderByIdDesc(request.email(), REGISTER)
                .orElseThrow(() -> new ApplicationException(ErrorCode.OTP_INVALID));
        if (code.getUsedAt() != null || !now.isBefore(code.getExpiresAt()))
            throw new ApplicationException(ErrorCode.OTP_INVALID);
        if (code.getAttemptCount() >= MAX_WRONG_ATTEMPTS)
            throw new ApplicationException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        if (!otpHash.matches(request.email(), REGISTER, request.verificationCode(), code.getCodeHash())) {
            int attempts = code.getAttemptCount() + 1;
            code.setAttemptCount(attempts);
            throw new WrongOtpAttempt(attempts == MAX_WRONG_ATTEMPTS
                    ? ErrorCode.OTP_ATTEMPTS_EXCEEDED : ErrorCode.OTP_INVALID);
        }

        var reader = roles.findByName("READER").orElseThrow(() -> new ApplicationException(ErrorCode.INTERNAL_ERROR));
        var user = new User();
        user.setUserName(request.userName());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        var saved = users.save(user);
        userRoles.save(new UserRole(new UserRoleId(saved.getId(), reader.getId())));
        code.setUsedAt(now);
        return new UserProfile(saved.getId(), saved.getUserName(), saved.getEmail(), saved.getFullName(),
                saved.getPhoneNumber(), saved.getStatus(), List.of(reader.getName()), true, List.of());
    }

    private Instant lockEmail(String email) {
        // Khóa advisory bảo vệ cả khi chưa có bản ghi OTP cho email này.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSetExtractor<Void>) result -> null, "library-user-service:otp:REGISTER:" + email);
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // Chỉ lỗi phát sinh sau khi tăng bộ đếm mới được commit cùng kết quả HTTP 400.
    static final class WrongOtpAttempt extends ApplicationException {
        WrongOtpAttempt(ErrorCode code) { super(code); }
    }
}
