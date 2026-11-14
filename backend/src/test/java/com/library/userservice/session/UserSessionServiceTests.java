package com.library.userservice.session;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.dto.SessionQuery;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.session.service.UserSessionService;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSessionServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final Clock clock = mock(Clock.class);
    private final UserSessionService service = new UserSessionService(users, sessions, tokens, clock);
    private final UserPrincipal caller = new UserPrincipal(3, UUID.randomUUID(), List.of());
    private final UUID targetId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-14T00:00:00Z");
    private User user;
    private UserSession current;
    private UserSession target;

    @BeforeEach void setup() {
        user = new User(); user.setStatus(UserStatus.ACTIVE);
        current = session(caller.sessionId()); target = session(targetId);
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        when(sessions.findById(caller.sessionId())).thenReturn(Optional.of(current));
        when(sessions.findById(targetId)).thenReturn(Optional.of(target));
        when(clock.instant()).thenReturn(now);
    }

    @Test void listsOnlyRepositoryPageWithVerifiedCurrentFlagAndStoredMetadata() {
        var row = mock(UserSession.class);
        when(row.getId()).thenReturn(caller.sessionId()); when(row.getCreatedAt()).thenReturn(now.minusSeconds(60));
        when(row.getLastSeenAt()).thenReturn(now.minusSeconds(30)); when(row.getExpiresAt()).thenReturn(now.plusSeconds(60));
        when(sessions.countActiveByUserId(3, now)).thenReturn(3L);
        when(sessions.findActiveWindow(3, now, 2, 0L)).thenReturn(List.of(row));
        var result = service.list(caller, new SessionQuery(0, 2));
        assertThat(result.totalElements()).isEqualTo(3); assertThat(result.page()).isZero(); assertThat(result.size()).isEqualTo(2);
        var item = result.items().getFirst(); assertThat(item.isCurrent()).isTrue();
        assertThat(item.lastSeenAt()).isEqualTo(now.minusSeconds(30));
        assertThat(item.deviceName()).isNull(); assertThat(item.clientType()).isNull();
        verify(users, never()).findByIdForUpdate(any()); verifyNoInteractions(tokens);
        verify(sessions, never()).save(any());
    }

    @Test void hugeValidPageUsesLongOffsetAndReturnsEmptyWithoutLoadingRows() {
        when(sessions.countActiveByUserId(3, now)).thenReturn(3L);
        var result = service.list(caller, new SessionQuery(Integer.MAX_VALUE, 100));
        assertThat(result.items()).isEmpty(); assertThat(result.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.size()).isEqualTo(100); assertThat(result.totalElements()).isEqualTo(3);
        verify(sessions, never()).findActiveWindow(any(), any(), anyInt(), anyLong());
        when(sessions.countActiveByUserId(3, now)).thenReturn(300_000_000_000L);
        when(sessions.findActiveWindow(3, now, 100, 214_748_364_700L)).thenReturn(List.of());
        service.list(caller, new SessionQuery(Integer.MAX_VALUE, 100));
        verify(sessions).findActiveWindow(3, now, 100, 214_748_364_700L);
    }

    @Test void revokeUsesTimeAfterLockAndOnlyTouchesSelectedSession() {
        service.revoke(caller, targetId);
        var order = inOrder(users, clock, sessions, tokens);
        order.verify(users).findByIdForUpdate(3); order.verify(clock).instant();
        order.verify(sessions).findById(caller.sessionId()); order.verify(sessions).findById(targetId);
        order.verify(sessions).save(target); order.verify(tokens).revokeActiveBySession(targetId, 3, now);
        assertThat(target.getRevokedAt()).isEqualTo(now); assertThat(target.getRevocationReason()).isEqualTo("SESSION_REVOKED");
        assertThat(current.getRevokedAt()).isNull();
    }

    @Test void rejectsCurrentUnknownAndForeignTargetsWithoutWriting() {
        error(() -> service.revoke(caller, caller.sessionId()), ErrorCode.CURRENT_SESSION_REVOKE_NOT_ALLOWED);
        when(sessions.findById(targetId)).thenReturn(Optional.empty());
        error(() -> service.revoke(caller, targetId), ErrorCode.SESSION_NOT_FOUND);
        when(sessions.findById(targetId)).thenReturn(Optional.of(target)); target.setUserId(4);
        error(() -> service.revoke(caller, targetId), ErrorCode.SESSION_NOT_FOUND);
        verify(sessions, never()).save(any()); verifyNoInteractions(tokens);
    }

    @Test void completedTargetKeepsReasonTimestampAndDoesNotRevokeTokens() {
        target.setRevokedAt(now.minusSeconds(10)); target.setRevocationReason("LOGOUT");
        service.revoke(caller, targetId);
        assertThat(target.getRevokedAt()).isEqualTo(now.minusSeconds(10)); assertThat(target.getRevocationReason()).isEqualTo("LOGOUT");
        target.setRevokedAt(null); target.setRevocationReason(null); target.setExpiresAt(now);
        service.revoke(caller, targetId);
        assertThat(target.getRevokedAt()).isNull(); verify(sessions, never()).save(any()); verifyNoInteractions(tokens);
    }

    @Test void bulkRevokesTokensBeforeSessionsAndUsesActualSessionCount() {
        when(tokens.revokeActiveByOtherActiveSessions(3, caller.sessionId(), now)).thenReturn(9);
        when(sessions.revokeActiveOthers(3, caller.sessionId(), now, "OTHER_SESSIONS_REVOKED")).thenReturn(2, 0);
        assertThat(service.revokeOthers(caller).revokedCount()).isEqualTo(2);
        var order = inOrder(users, clock, sessions, tokens);
        order.verify(users).findByIdForUpdate(3); order.verify(clock).instant();
        order.verify(sessions).findById(caller.sessionId());
        order.verify(tokens).revokeActiveByOtherActiveSessions(3, caller.sessionId(), now);
        order.verify(sessions).revokeActiveOthers(3, caller.sessionId(), now, "OTHER_SESSIONS_REVOKED");
        assertThat(service.revokeOthers(caller).revokedCount()).isZero(); verify(sessions, never()).save(any());
    }

    @Test void everyOperationRejectsMissingBannedWrongOwnerRevokedOrExpiredCaller() {
        when(users.findById(3)).thenReturn(Optional.empty()); when(users.findByIdForUpdate(3)).thenReturn(Optional.empty());
        inactiveOnAll();
        when(users.findById(3)).thenReturn(Optional.of(user)); when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        user.setStatus(UserStatus.BANNED); inactiveOnAll(); user.setStatus(UserStatus.ACTIVE);
        when(sessions.findById(caller.sessionId())).thenReturn(Optional.empty()); inactiveOnAll();
        when(sessions.findById(caller.sessionId())).thenReturn(Optional.of(current));
        current.setUserId(4); inactiveOnAll(); current.setUserId(3);
        current.setRevokedAt(now); inactiveOnAll(); current.setRevokedAt(null);
        current.setExpiresAt(now); inactiveOnAll(); current.setExpiresAt(now.minusSeconds(1)); inactiveOnAll();
        verifyNoInteractions(tokens); verify(sessions, never()).save(any());
        verify(sessions, never()).findById(targetId);
    }

    @Test void rechecksSessionExpiringWhileWaitingForLockBeforeTargetValidation() {
        when(users.findByIdForUpdate(3)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(current.getExpiresAt()); return Optional.of(user);
        });
        error(() -> service.revoke(caller, caller.sessionId()), ErrorCode.SESSION_INACTIVE);
        error(() -> service.revokeOthers(caller), ErrorCode.SESSION_INACTIVE);
        verifyNoInteractions(tokens); verify(sessions, never()).save(any());
    }

    @Test void tokenFailureDoesNotProceedToBulkSessionUpdate() {
        when(tokens.revokeActiveByOtherActiveSessions(any(), any(), any())).thenThrow(new IllegalStateException("fixture"));
        assertThatThrownBy(() -> service.revokeOthers(caller)).isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).revokeActiveOthers(any(), any(), any(), any());
    }

    private UserSession session(UUID id) {
        var s = new UserSession(); s.setId(id); s.setUserId(3); s.setExpiresAt(now.plusSeconds(60)); return s;
    }
    private void inactiveOnAll() {
        error(() -> service.list(caller, new SessionQuery()), ErrorCode.SESSION_INACTIVE);
        error(() -> service.revoke(caller, targetId), ErrorCode.SESSION_INACTIVE);
        error(() -> service.revokeOthers(caller), ErrorCode.SESSION_INACTIVE);
    }
    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
