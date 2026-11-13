package com.library.userservice.user.service;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.ChangePasswordRequest;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangePasswordService {
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordService passwords;
    private final Clock clock;

    public ChangePasswordService(UserRepository users, UserSessionRepository sessions,
            RefreshTokenRepository refreshTokens, PasswordService passwords, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        // Cùng khóa với login/refresh/logout/PATCH; không dùng entity từ bước Bearer.
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow(ChangePasswordService::inactive);
        var now = clock.instant();
        if (user.getStatus() != UserStatus.ACTIVE) throw inactive();
        var session = sessions.findById(principal.sessionId()).orElseThrow(ChangePasswordService::inactive);
        if (!Integer.valueOf(principal.userId()).equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) throw inactive();

        if (user.getPasswordHash() == null) throw new ApplicationException(ErrorCode.PASSWORD_NOT_CONFIGURED);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash()))
            throw new ApplicationException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        user.setPasswordHash(passwords.encode(request.newPassword()));
        users.save(user);
        // Bulk update flush password trước khi thu hồi; không save lại entity phiên đã đọc.
        sessions.revokeActiveByUser(principal.userId(), now, "PASSWORD_CHANGED");
        refreshTokens.revokeActiveByUser(principal.userId(), now);
        // Giữ lịch sử đã kết thúc; caller chỉ nhận thành công sau COMMIT.
    }

    private static ApplicationException inactive() { return new ApplicationException(ErrorCode.SESSION_INACTIVE); }
}
