package com.library.userservice.auth;

import com.library.userservice.auth.dto.RefreshTokenRequest;
import com.library.userservice.auth.persistence.RefreshToken;
import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.auth.service.LogoutService;
import com.library.userservice.auth.service.RefreshService;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.RefreshTokenGenerator;
import com.library.userservice.security.UserAccessTokenService;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshLogoutServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();
    private final UserAccessTokenService jwt = mock(UserAccessTokenService.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-11-11T00:00:00Z");
    private final UUID sid = UUID.randomUUID();
    private final RefreshTokenRequest request = new RefreshTokenRequest("  nguyên dạng  ");
    private final RefreshService refresh = new RefreshService(users, sessions, tokens, generator, jwt, clock);
    private final LogoutService logout = new LogoutService(users, sessions, tokens, generator, clock);
    private RefreshToken old;
    private User user;
    private UserSession session;

    @BeforeEach void setup() {
        old = new RefreshToken(); ReflectionTestUtils.setField(old, "id", 10);
        old.setUserId(3); old.setSessionId(sid); old.setExpiresAt(now.plusSeconds(60));
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        session = new UserSession(); session.setId(sid); session.setUserId(3); session.setExpiresAt(now.plusSeconds(120));
        when(clock.instant()).thenReturn(now);
        when(tokens.findOwnerByTokenHash(generator.hash(request.refreshToken()))).thenReturn(Optional.of(3));
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(generator.hash(request.refreshToken()))).thenReturn(Optional.of(old));
        when(sessions.findById(sid)).thenReturn(Optional.of(session));
        when(tokens.save(any())).thenAnswer(call -> {
            RefreshToken token = call.getArgument(0);
            if (token.getId() == null) ReflectionTestUtils.setField(token, "id", 11);
            return token;
        });
        when(jwt.issue(3, sid)).thenReturn("fixture-access");
    }

    @Test void rotationKeepsSessionAndStoresOnlyHashWithIndependentSevenDayExpiry() {
        var result = refresh.refresh(request);
        var saved = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens, times(2)).save(saved.capture());
        var replacement = saved.getAllValues().get(0);
        assertThat(result.expiresIn()).isEqualTo(3600);
        assertThat(replacement.getSessionId()).isEqualTo(sid);
        assertThat(replacement.getUserId()).isEqualTo(3);
        assertThat(replacement.getTokenHash().equals(generator.hash(result.refreshToken()))).isTrue();
        assertThat(replacement.getExpiresAt()).isEqualTo(now.plusSeconds(604800));
        assertThat(old.getRevokedAt()).isEqualTo(now);
        assertThat(old.getReplacedByTokenId()).isEqualTo(11);
        verify(sessions, never()).save(any());
        var order = inOrder(tokens, users, sessions, jwt);
        order.verify(tokens).findOwnerByTokenHash(generator.hash(request.refreshToken()));
        order.verify(users).findByIdForUpdate(3);
        order.verify(tokens).findByTokenHash(generator.hash(request.refreshToken()));
        order.verify(sessions).findById(sid);
    }

    @Test void unknownTokenAndTokenRemovedWhileWaitingAreInvalid() {
        when(tokens.findOwnerByTokenHash(anyString())).thenReturn(Optional.empty());
        assertError(ErrorCode.REFRESH_TOKEN_INVALID);
        verifyNoInteractions(users);
        when(tokens.findOwnerByTokenHash(anyString())).thenReturn(Optional.of(3));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertError(ErrorCode.REFRESH_TOKEN_INVALID);
        verify(tokens, never()).save(any());
    }

    @Test void tokenExpiryAtBoundaryAndRevocationTakePrecedenceOverBannedOrInactiveSession() {
        user.setStatus(UserStatus.BANNED); session.setRevokedAt(now.minusSeconds(1));
        old.setExpiresAt(now);
        assertError(ErrorCode.REFRESH_TOKEN_INVALID);
        old.setExpiresAt(now.plusSeconds(1)); old.setRevokedAt(now.minusSeconds(1));
        assertError(ErrorCode.REFRESH_TOKEN_INVALID);
        verifyNoInteractions(jwt); verify(sessions, never()).findById(any());
        verify(tokens, never()).save(any());
    }

    @Test void bannedAccountPrecedesInactiveSessionAndMissingAccountIsInactive() {
        user.setStatus(UserStatus.BANNED); session.setExpiresAt(now);
        assertError(ErrorCode.ACCOUNT_BANNED);
        when(users.findByIdForUpdate(3)).thenReturn(Optional.empty());
        assertError(ErrorCode.SESSION_INACTIVE);
        verify(tokens, never()).save(any());
    }

    @Test void sessionHasNoClockSkewAndMustBelongToTokenOwner() {
        session.setExpiresAt(now); assertError(ErrorCode.SESSION_INACTIVE);
        session.setExpiresAt(now.plusNanos(1));
        session.setRevokedAt(now.minusSeconds(1)); assertError(ErrorCode.SESSION_INACTIVE);
        session.setRevokedAt(null); session.setUserId(4); assertError(ErrorCode.SESSION_INACTIVE);
        when(sessions.findById(sid)).thenReturn(Optional.empty()); assertError(ErrorCode.SESSION_INACTIVE);
        verify(tokens, never()).save(any());
    }

    @Test void clockIsReadAfterAcquiringUserLock() {
        var time = new AtomicReference<>(now.minusSeconds(2));
        when(clock.instant()).thenAnswer(call -> time.get());
        old.setExpiresAt(now);
        when(users.findByIdForUpdate(3)).thenAnswer(call -> { time.set(now); return Optional.of(user); });
        assertError(ErrorCode.REFRESH_TOKEN_INVALID);
        verify(tokens, never()).save(any());
    }

    @Test void logoutAcceptsExpiredRotatedTokenAndBannedUser() {
        user.setStatus(UserStatus.BANNED);
        old.setExpiresAt(now.minusSeconds(1)); old.setRevokedAt(now.minusSeconds(10)); old.setReplacedByTokenId(11);
        logout.logout(request);
        assertThat(session.getRevokedAt()).isEqualTo(now);
        assertThat(session.getRevocationReason()).isEqualTo("LOGOUT");
        verify(tokens).revokeActiveBySession(sid, 3, now);
        assertThat(old.getRevokedAt()).isEqualTo(now.minusSeconds(10));
        assertThat(old.getReplacedByTokenId()).isEqualTo(11);
        verifyNoInteractions(jwt);
    }

    @Test void logoutPreservesEndedSessionAndDoesNotWriteTokens() {
        session.setRevokedAt(now.minusSeconds(3)); session.setRevocationReason("PASSWORD_CHANGED");
        logout.logout(request);
        assertThat(session.getRevokedAt()).isEqualTo(now.minusSeconds(3));
        assertThat(session.getRevocationReason()).isEqualTo("PASSWORD_CHANGED");
        session.setRevokedAt(null); session.setRevocationReason(null); session.setExpiresAt(now);
        logout.logout(request);
        assertThat(session.getRevokedAt()).isNull();
        verify(sessions, never()).save(any());
        verify(tokens, never()).revokeActiveBySession(any(), any(), any());
    }

    @Test void logoutUnknownRemovedOrUnresolvableTokenIsNoOp() {
        when(tokens.findOwnerByTokenHash(anyString())).thenReturn(Optional.empty()); logout.logout(request);
        verifyNoInteractions(users);
        when(tokens.findOwnerByTokenHash(anyString())).thenReturn(Optional.of(3));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty()); logout.logout(request);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(old));
        when(sessions.findById(sid)).thenReturn(Optional.empty()); logout.logout(request);
        when(sessions.findById(sid)).thenReturn(Optional.of(session)); session.setUserId(4); logout.logout(request);
        verify(sessions, never()).save(any());
        verify(tokens, never()).revokeActiveBySession(any(), any(), any());
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> refresh.refresh(request)).isInstanceOfSatisfying(ApplicationException.class,
                failure -> assertThat(failure.code()).isEqualTo(expected));
    }
}
