package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenGenerator generator;
    private final Clock clock;

    public LogoutService(UserRepository users, UserSessionRepository sessions, RefreshTokenRepository refreshTokens,
            RefreshTokenGenerator generator, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void logout(RefreshTokenRequest request) {
        String hash = generator.hash(request.refreshToken());
        var owner = refreshTokens.findOwnerByTokenHash(hash);
        if (owner.isEmpty()) return;
        if (users.findByIdForUpdate(owner.orElseThrow()).isEmpty()) return;
        var token = refreshTokens.findByTokenHash(hash);
        if (token.isEmpty() || !owner.orElseThrow().equals(token.orElseThrow().getUserId())) return;
        var candidate = sessions.findById(token.orElseThrow().getSessionId());
        if (candidate.isEmpty()) return;
        var session = candidate.orElseThrow();
        var now = clock.instant();
        if (!owner.orElseThrow().equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) return;

        // Không xét hạn/trạng thái token cũ hoặc trạng thái user: chỉ xác định đúng phiên.
        session.setRevokedAt(now);
        session.setRevocationReason("LOGOUT");
        sessions.save(session);
        refreshTokens.revokeActiveBySession(session.getId(), owner.orElseThrow(), now);
    }
}
