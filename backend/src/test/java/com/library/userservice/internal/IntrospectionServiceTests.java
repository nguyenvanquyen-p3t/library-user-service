package com.library.userservice.internal;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.IntrospectionActiveResponse;
import com.library.userservice.internal.dto.IntrospectionInactiveResponse;
import com.library.userservice.internal.dto.IntrospectionRequest;
import com.library.userservice.internal.service.IntrospectionService;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.security.UserSessionAuthenticator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntrospectionServiceTests {
    private final UserAccessTokenService tokens = mock(UserAccessTokenService.class);
    private final UserSessionAuthenticator sessions = mock(UserSessionAuthenticator.class);
    private final IntrospectionService service = new IntrospectionService(tokens, sessions);
    private final UUID sid = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("verified").header("alg", "RS256").subject("3")
            .issuer("urn:library:user-service").audience(List.of("library-api"))
            .issuedAt(Instant.ofEpochSecond(100)).expiresAt(Instant.ofEpochSecond(3700)).build();

    @Test void mapsVerifiedClaimsAndCurrentRolesIgnoringHintAndPreservingToken() {
        when(tokens.verify(" token ")).thenReturn(jwt);
        when(sessions.authenticate(jwt)).thenReturn(new UserPrincipal(3, sid, List.of("ADMIN", "READER")));
        var result = (IntrospectionActiveResponse) service.introspect(new IntrospectionRequest(" token ", "refresh_token"));
        assertThat(result).isEqualTo(new IntrospectionActiveResponse("3", sid, "Bearer", "urn:library:user-service",
                List.of("library-api"), 3700, 100, List.of("ADMIN", "READER")));
        verify(tokens).verify(" token "); verify(sessions).authenticate(jwt);
    }

    @Test void invalidTokenDoesNotQuerySessionOrDatabase() {
        when(tokens.verify("bad")).thenThrow(new ApplicationException(ErrorCode.ACCESS_TOKEN_INVALID));
        assertThat(service.introspect(new IntrospectionRequest("bad", null))).isInstanceOf(IntrospectionInactiveResponse.class);
        verifyNoInteractions(sessions);
    }

    @Test void inactiveSessionBecomesInactiveAfterAuthenticatorReturnsThroughItsProxy() {
        when(tokens.verify("token")).thenReturn(jwt);
        when(sessions.authenticate(jwt)).thenThrow(new ApplicationException(ErrorCode.SESSION_INACTIVE));
        assertThat(service.introspect(new IntrospectionRequest("token", null))).isInstanceOf(IntrospectionInactiveResponse.class);
    }

    @Test void doesNotHideInfrastructureOrUnexpectedApplicationFailures() {
        when(tokens.verify("token")).thenReturn(jwt);
        for (var failure : List.of(new DataAccessResourceFailureException("private-db"),
                new IllegalStateException("private-error"), new ApplicationException(ErrorCode.ACCESS_DENIED))) {
            doThrow(failure).when(sessions).authenticate(jwt);
            assertThatThrownBy(() -> service.introspect(new IntrospectionRequest("token", null))).isSameAs(failure);
        }
    }
}
