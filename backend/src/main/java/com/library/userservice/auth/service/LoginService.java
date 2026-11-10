package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.auth.persistence.RefreshToken;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.session.persistence.SessionCreationRepository;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordService passwords;
    private final SessionCreationRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenGenerator generator;
    private final UserAccessTokenService accessTokens;
    private final Clock clock;

    public LoginService(UserRepository users, PasswordService passwords, SessionCreationRepository sessions,
            RefreshTokenRepository refreshTokens, RefreshTokenGenerator generator, UserAccessTokenService accessTokens, Clock clock) {
        this.users = users; this.passwords = passwords; this.sessions = sessions; this.refreshTokens = refreshTokens;
        this.generator = generator; this.accessTokens = accessTokens; this.clock = clock;
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        var candidate = request.identifier().contains("@") ? users.findByEmailForUpdate(request.identifier())
                : users.findByUserNameForUpdate(request.identifier());
        if (candidate.isEmpty()) {
            passwords.matches(request.password(), null);
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        var user = candidate.orElseThrow();
        if (user.getPasswordHash() == null) throw new ApplicationException(ErrorCode.PASSWORD_NOT_CONFIGURED);
        if (!passwords.matches(request.password(), user.getPasswordHash())) throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        if (user.getStatus() != UserStatus.ACTIVE) throw new ApplicationException(ErrorCode.ACCOUNT_BANNED);
        UUID sid = UUID.randomUUID();
        sessions.create(sid, user.getId());
        String refresh = generator.generate();
        var token = new RefreshToken();
        token.setUserId(user.getId()); token.setSessionId(sid); token.setTokenHash(generator.hash(refresh));
        token.setExpiresAt(clock.instant().plusSeconds(604800));
        refreshTokens.save(token);
        // Mọi lỗi ký/lưu/commit rollback transaction; chỉ caller ngoài proxy mới nhận được kết quả.
        return new TokenPair(accessTokens.issue(user.getId(), sid), refresh);
    }
}
