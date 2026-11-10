package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caller phải xác minh chữ ký/claim trước khi dùng kết quả JWT để truy vấn dữ liệu. */
@Service
public class UserSessionAuthenticator {
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RoleRepository roles;
    private final Clock clock;

    public UserSessionAuthenticator(UserRepository users, UserSessionRepository sessions, RoleRepository roles, Clock clock) {
        this.users = users; this.sessions = sessions; this.roles = roles; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserPrincipal authenticate(Jwt verifiedJwt) {
        int userId = Integer.parseInt(verifiedJwt.getSubject());
        UUID sid = UUID.fromString(verifiedJwt.getClaimAsString("sid"));
        var user = users.findById(userId).orElseThrow(UserSessionAuthenticator::inactive);
        if (user.getStatus() != UserStatus.ACTIVE) throw inactive();
        var session = sessions.findById(sid).orElseThrow(UserSessionAuthenticator::inactive);
        if (!Integer.valueOf(userId).equals(session.getUserId()) || session.getRevokedAt() != null
                || !clock.instant().isBefore(session.getExpiresAt())) throw inactive();
        return new UserPrincipal(userId, sid, roles.findNamesByUserId(userId));
    }

    private static ApplicationException inactive() { return new ApplicationException(ErrorCode.SESSION_INACTIVE); }
}
