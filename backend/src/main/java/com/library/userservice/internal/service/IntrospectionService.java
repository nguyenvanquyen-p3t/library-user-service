package com.library.userservice.internal.service;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.IntrospectionActiveResponse;
import com.library.userservice.internal.dto.IntrospectionInactiveResponse;
import com.library.userservice.internal.dto.IntrospectionRequest;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserSessionAuthenticator;
import org.springframework.stereotype.Service;

@Service
public class IntrospectionService {
    private final UserAccessTokenService tokens;
    private final UserSessionAuthenticator sessions;

    public IntrospectionService(UserAccessTokenService tokens, UserSessionAuthenticator sessions) {
        this.tokens = tokens;
        this.sessions = sessions;
    }

    // Không mở transaction bao ngoài: lỗi inactive được bắt sau khi transaction xác thực kết thúc.
    public Object introspect(IntrospectionRequest request) {
        try {
            var jwt = tokens.verify(request.token());
            var principal = sessions.authenticate(jwt);
            return new IntrospectionActiveResponse(jwt.getSubject(), principal.sessionId(), "Bearer",
                    jwt.getClaimAsString("iss"), jwt.getAudience(), jwt.getExpiresAt().getEpochSecond(),
                    jwt.getIssuedAt().getEpochSecond(), principal.roles());
        } catch (ApplicationException exception) {
            if (exception.code() == ErrorCode.ACCESS_TOKEN_INVALID || exception.code() == ErrorCode.SESSION_INACTIVE)
                return new IntrospectionInactiveResponse();
            throw exception;
        }
    }
}
