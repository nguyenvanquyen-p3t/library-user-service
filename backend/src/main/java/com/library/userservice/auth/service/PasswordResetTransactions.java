package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.PASSWORD_RESET;

import com.library.userservice.auth.dto.ResetPasswordRequest;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetTransactions {
    private final EmailOtpService otp;
    private final OtpMailSender mail;
    private final UserRepository users;
    private final PasswordService passwords;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;

    PasswordResetTransactions(EmailOtpService otp, OtpMailSender mail, UserRepository users,
            PasswordService passwords, UserSessionRepository sessions, RefreshTokenRepository refreshTokens) {
        this.otp = otp;
        this.mail = mail;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<String> issue(String email) {
        otp.lock(email, PASSWORD_RESET);
        var user = users.findByEmail(email);
        if (user.isEmpty()) return Optional.empty();

        requirePassword(user.get());
        otp.requireConfigured();
        mail.requireConfigured();
        return Optional.of(otp.issue(email, PASSWORD_RESET, otp.now()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = EmailOtpService.WrongOtpAttempt.class)
    public void reset(ResetPasswordRequest request) {
        otp.lock(request.email(), PASSWORD_RESET);
        var user = users.findByEmailForUpdate(request.email())
                .orElseThrow(() -> new ApplicationException(ErrorCode.OTP_INVALID));
        var now = otp.now();
        requirePassword(user);
        var code = otp.verify(request.email(), PASSWORD_RESET, request.verificationCode(), now);

        user.setPasswordHash(passwords.encode(request.newPassword()));
        code.setUsedAt(now);
        sessions.revokeActiveByUser(user.getId(), now, PASSWORD_RESET.name());
        refreshTokens.revokeActiveByUser(user.getId(), now);
    }

    private static void requirePassword(User user) {
        if (user.getPasswordHash() == null) throw new ApplicationException(ErrorCode.PASSWORD_NOT_CONFIGURED);
    }
}
