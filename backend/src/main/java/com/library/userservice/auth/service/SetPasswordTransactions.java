package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.SET_PASSWORD;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.SetPasswordRequest;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SetPasswordTransactions {
    private final EmailOtpService otp;
    private final OtpMailSender mail;
    private final UserRepository users;
    private final PasswordService passwords;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;

    SetPasswordTransactions(EmailOtpService otp, OtpMailSender mail, UserRepository users,
            PasswordService passwords, UserSessionRepository sessions, RefreshTokenRepository refreshTokens) {
        this.otp = otp;
        this.mail = mail;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IssuedCode issue(UserPrincipal principal) {
        var account = lockAccount(principal);
        otp.requireConfigured();
        mail.requireConfigured();
        String email = account.user().getEmail();
        return new IssuedCode(email, otp.issue(email, SET_PASSWORD, account.now()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = EmailOtpService.WrongOtpAttempt.class)
    public void setPassword(UserPrincipal principal, SetPasswordRequest request) {
        var account = lockAccount(principal);
        var user = account.user();
        var now = account.now();
        var code = otp.verify(user.getEmail(), SET_PASSWORD, request.verificationCode(), now);

        user.setPasswordHash(passwords.encode(request.newPassword()));
        code.setUsedAt(now);
        sessions.revokeActiveByUser(user.getId(), now, "PASSWORD_SET");
        refreshTokens.revokeActiveByUser(user.getId(), now);
    }

    private LockedAccount lockAccount(UserPrincipal principal) {
        // Chỉ đọc email trước khóa để không giữ entity user cũ trong persistence context.
        String email = users.findEmailById(principal.userId()).orElseThrow(SetPasswordTransactions::inactive);
        otp.lock(email, SET_PASSWORD);
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow(SetPasswordTransactions::inactive);
        var now = otp.now();
        if (user.getStatus() != UserStatus.ACTIVE || !email.equals(user.getEmail())) throw inactive();

        var session = sessions.findById(principal.sessionId()).orElseThrow(SetPasswordTransactions::inactive);
        if (!user.getId().equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) throw inactive();
        if (user.getPasswordHash() != null) throw new ApplicationException(ErrorCode.PASSWORD_ALREADY_CONFIGURED);
        return new LockedAccount(user, now);
    }

    private static ApplicationException inactive() {
        return new ApplicationException(ErrorCode.SESSION_INACTIVE);
    }

    record IssuedCode(String email, String code) {
        @Override public String toString() { return "IssuedCode[REDACTED]"; }
    }

    private record LockedAccount(User user, Instant now) {}
}
