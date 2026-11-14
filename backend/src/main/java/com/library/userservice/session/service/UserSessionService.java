package com.library.userservice.session.service;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.dto.RevokeOthersResult;
import com.library.userservice.session.dto.SessionItem;
import com.library.userservice.session.dto.SessionPage;
import com.library.userservice.session.dto.SessionQuery;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSessionService {
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final Clock clock;

    public UserSessionService(UserRepository users, UserSessionRepository sessions,
            RefreshTokenRepository tokens, Clock clock) {
        this.users = users; this.sessions = sessions; this.tokens = tokens; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SessionPage list(UserPrincipal principal, SessionQuery query) {
        var user = users.findById(principal.userId()).orElseThrow(UserSessionService::inactive);
        var now = clock.instant();
        checkCaller(principal, user, now);
        long total = sessions.countActiveByUserId(principal.userId(), now);
        long offset = (long) query.page() * query.size();
        var items = offset >= total ? List.<SessionItem>of()
                : sessions.findActiveWindow(principal.userId(), now, query.size(), offset).stream()
                        .map(s -> new SessionItem(s.getId(), s.getDeviceName(), s.getClientType(), s.getCreatedAt(),
                                s.getLastSeenAt(), s.getExpiresAt(), principal.sessionId().equals(s.getId())))
                        .toList();
        return new SessionPage(items, query.page(), query.size(), total);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(UserPrincipal principal, UUID targetSid) {
        var now = lockAndCheckCaller(principal);
        if (principal.sessionId().equals(targetSid))
            throw new ApplicationException(ErrorCode.CURRENT_SESSION_REVOKE_NOT_ALLOWED);
        var target = sessions.findById(targetSid).filter(s -> Integer.valueOf(principal.userId()).equals(s.getUserId()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.SESSION_NOT_FOUND));
        if (target.getRevokedAt() != null || !now.isBefore(target.getExpiresAt())) return;
        target.setRevokedAt(now);
        target.setRevocationReason("SESSION_REVOKED");
        sessions.save(target);
        tokens.revokeActiveBySession(targetSid, principal.userId(), now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RevokeOthersResult revokeOthers(UserPrincipal principal) {
        var now = lockAndCheckCaller(principal);
        // Token trước phiên: subquery phải còn thấy các phiên đích đang hiệu lực.
        tokens.revokeActiveByOtherActiveSessions(principal.userId(), principal.sessionId(), now);
        long count = sessions.revokeActiveOthers(principal.userId(), principal.sessionId(), now, "OTHER_SESSIONS_REVOKED");
        // Không save lại entity phiên đã đọc trước bulk update; chỉ trả kết quả sau commit.
        return new RevokeOthersResult(count);
    }

    private Instant lockAndCheckCaller(UserPrincipal principal) {
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow(UserSessionService::inactive);
        var now = clock.instant();
        checkCaller(principal, user, now);
        return now;
    }

    private void checkCaller(UserPrincipal principal, User user, Instant now) {
        if (user.getStatus() != UserStatus.ACTIVE) throw inactive();
        var caller = sessions.findById(principal.sessionId()).orElseThrow(UserSessionService::inactive);
        if (!Integer.valueOf(principal.userId()).equals(caller.getUserId()) || caller.getRevokedAt() != null
                || !now.isBefore(caller.getExpiresAt())) throw inactive();
    }

    private static ApplicationException inactive() { return new ApplicationException(ErrorCode.SESSION_INACTIVE); }
}
