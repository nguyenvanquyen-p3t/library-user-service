package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.persistence.RefreshToken;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.session.persistence.SessionCreationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Caller đã xác minh user và giữ transaction tạo phiên. */
@Component
class LoginSessionIssuer {
    private final SessionCreationRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenGenerator generator;
    private final UserAccessTokenService accessTokens;
    private final Clock clock;

    LoginSessionIssuer(SessionCreationRepository sessions, RefreshTokenRepository refreshTokens,
            RefreshTokenGenerator generator, UserAccessTokenService accessTokens, Clock clock) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    TokenPair issue(int userId) {
        UUID sid = UUID.randomUUID();
        sessions.create(sid, userId);
        String refresh = generator.generate();
        var token = new RefreshToken();
        token.setUserId(userId);
        token.setSessionId(sid);
        token.setTokenHash(generator.hash(refresh));
        token.setExpiresAt(clock.instant().plusSeconds(604800));
        refreshTokens.save(token);
        return new TokenPair(accessTokens.issue(userId, sid), refresh);
    }
}
