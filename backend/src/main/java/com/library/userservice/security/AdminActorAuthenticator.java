package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminActorAuthenticator {
    private final UserAccessTokenService tokens;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RoleRepository roles;
    private final Clock clock;

    public AdminActorAuthenticator(UserAccessTokenService tokens, UserRepository users,
            UserSessionRepository sessions, RoleRepository roles, Clock clock) {
        this.tokens = tokens; this.users = users; this.sessions = sessions; this.roles = roles; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminActor authenticate(String token) { return authenticateAt(token, clock.instant()); }

    /** Tham gia transaction writer hiện hữu; caller lấy now sau khi khóa cả actor/đích. */
    @Transactional(readOnly = true)
    public AdminActor authenticateAt(String token, Instant now) {
        org.springframework.security.oauth2.jwt.Jwt jwt;
        try { jwt = tokens.verify(token); }
        catch (ApplicationException exception) {
            if (exception.code() == ErrorCode.ACCESS_TOKEN_INVALID) throw invalid();
            throw exception;
        }
        int id = Integer.parseInt(jwt.getSubject());
        UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
        var user = users.findById(id).orElseThrow(AdminActorAuthenticator::invalid);
        var session = sessions.findById(sid).orElseThrow(AdminActorAuthenticator::invalid);
        if (!Integer.valueOf(id).equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) throw invalid();
        if (user.getStatus() != UserStatus.ACTIVE || !roles.findNamesByUserId(id).contains("ADMIN"))
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return new AdminActor(id, sid, token);
    }

    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.ADMIN_ACTOR_INVALID); }
}
