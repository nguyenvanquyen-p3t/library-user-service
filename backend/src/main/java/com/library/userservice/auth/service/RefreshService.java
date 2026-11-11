package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.persistence.RefreshToken;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshService {
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenGenerator generator;
    private final UserAccessTokenService accessTokens;
    private final Clock clock;

    public RefreshService(UserRepository users, UserSessionRepository sessions, RefreshTokenRepository refreshTokens,
            RefreshTokenGenerator generator, UserAccessTokenService accessTokens, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TokenPair refresh(RefreshTokenRequest request) {
        String hash = generator.hash(request.refreshToken());
        int owner = refreshTokens.findOwnerByTokenHash(hash)
                .orElseThrow(() -> new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID));
        // Cùng khóa với login/logout và các writer password/status/revoke triển khai sau.
        var user = users.findByIdForUpdate(owner);
        var token = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID));
        var now = clock.instant();
        if (!Integer.valueOf(owner).equals(token.getUserId()) || token.getRevokedAt() != null
                || !now.isBefore(token.getExpiresAt()))
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        var account = user.orElseThrow(() -> new ApplicationException(ErrorCode.SESSION_INACTIVE));
        if (account.getStatus() != UserStatus.ACTIVE) throw new ApplicationException(ErrorCode.ACCOUNT_BANNED);
        var session = sessions.findById(token.getSessionId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.SESSION_INACTIVE));
        if (!Integer.valueOf(owner).equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) throw new ApplicationException(ErrorCode.SESSION_INACTIVE);

        String refresh = generator.generate();
        var replacement = new RefreshToken();
        replacement.setUserId(owner);
        replacement.setSessionId(session.getId());
        replacement.setTokenHash(generator.hash(refresh));
        replacement.setExpiresAt(now.plusSeconds(604800));
        replacement = refreshTokens.save(replacement);
        token.setRevokedAt(now);
        token.setReplacedByTokenId(replacement.getId());
        refreshTokens.save(token);
        // Ký trước commit; mọi lỗi lưu/ký/commit đều rollback, không cấp lại bằng token cũ.
        return new TokenPair(accessTokens.issue(owner, session.getId()), refresh);
    }
}
