package com.library.userservice.user;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.ChangePasswordRequest;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import com.library.userservice.user.service.ChangePasswordService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChangePasswordServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final Clock clock = mock(Clock.class);
    private final ChangePasswordService service = new ChangePasswordService(users, sessions, tokens, passwords, clock);
    private final UserPrincipal principal = new UserPrincipal(3, UUID.randomUUID(), List.of());
    private final Instant now = Instant.parse("2026-11-13T00:00:00Z");
    private final ChangePasswordRequest request = new ChangePasswordRequest("old-test-only", "  mới 密  ", "  mới 密  ");
    private User user;
    private UserSession session;

    @BeforeEach void setup() {
        user = new User(); user.setStatus(UserStatus.ACTIVE); user.setPasswordHash("old-fixture-hash");
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(now.plusSeconds(60));
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(now);
        when(passwords.matches(request.currentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwords.encode(request.newPassword())).thenReturn("new-fixture-hash");
    }

    @Test void changesPasswordAndRevokesAllActiveSessionsAndTokensUsingTimeAfterLock() {
        service.changePassword(principal, request);
        var order = inOrder(users, clock, sessions, passwords, tokens);
        order.verify(users).findByIdForUpdate(3); order.verify(clock).instant();
        order.verify(sessions).findById(principal.sessionId());
        order.verify(passwords).matches(request.currentPassword(), "old-fixture-hash");
        order.verify(passwords).encode(request.newPassword()); order.verify(users).save(user);
        order.verify(sessions).revokeActiveByUser(3, now, "PASSWORD_CHANGED");
        order.verify(tokens).revokeActiveByUser(3, now);
        assertThat(user.getPasswordHash()).isEqualTo("new-fixture-hash");
        verify(users, never()).findById(anyInt()); verify(sessions, never()).save(any());
    }

    @Test void missingOrBannedUserTakesPrecedenceOverPasswordChecks() {
        when(users.findByIdForUpdate(3)).thenReturn(Optional.empty()); assertError(ErrorCode.SESSION_INACTIVE);
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        user.setStatus(UserStatus.BANNED); user.setPasswordHash(null); assertError(ErrorCode.SESSION_INACTIVE);
        verifyNoInteractions(passwords, tokens, sessions);
    }

    @Test void rejectsMissingWrongOwnerRevokedAndExpiredSessionWithoutWriting() {
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.empty()); assertError(ErrorCode.SESSION_INACTIVE);
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.of(session));
        session.setUserId(4); assertError(ErrorCode.SESSION_INACTIVE); session.setUserId(3);
        session.setRevokedAt(now); assertError(ErrorCode.SESSION_INACTIVE); session.setRevokedAt(null);
        session.setExpiresAt(now); assertError(ErrorCode.SESSION_INACTIVE);
        session.setExpiresAt(now.minusSeconds(1)); assertError(ErrorCode.SESSION_INACTIVE);
        verifyNoInteractions(passwords, tokens);
    }

    @Test void sessionExpiringWhileWaitingForLockIsInactive() {
        when(users.findByIdForUpdate(3)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(session.getExpiresAt()); return Optional.of(user);
        });
        assertError(ErrorCode.SESSION_INACTIVE); verifyNoInteractions(passwords, tokens);
    }

    @Test void distinguishesPasswordNotConfiguredAndIncorrectWithoutWrites() {
        user.setPasswordHash(null); assertError(ErrorCode.PASSWORD_NOT_CONFIGURED);
        verifyNoInteractions(passwords);
        user.setPasswordHash("unsupported-or-corrupt"); assertError(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        verify(passwords, never()).encode(anyString()); verifyNoInteractions(tokens);
    }

    @Test void encodingFailureDoesNotStartPersistence() {
        when(passwords.encode(anyString())).thenThrow(new IllegalStateException("private-uc07-encode"));
        assertThatThrownBy(() -> service.changePassword(principal, request)).isInstanceOf(IllegalStateException.class);
        assertThat(user.getPasswordHash()).isEqualTo("old-fixture-hash");
        verify(users, never()).save(any()); verifyNoInteractions(tokens);
    }

    private void assertError(ErrorCode code) {
        assertThatThrownBy(() -> service.changePassword(principal, request)).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.code()).isEqualTo(code));
        verify(users, never()).save(any()); verify(sessions, never()).revokeActiveByUser(anyInt(), any(), anyString());
    }
}
