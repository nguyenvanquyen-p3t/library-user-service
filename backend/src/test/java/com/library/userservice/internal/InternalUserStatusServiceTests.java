package com.library.userservice.internal;

import com.library.userservice.auth.persistence.RefreshTokenRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.UserStatusRequest;
import com.library.userservice.internal.service.InternalUserStatusService;
import com.library.userservice.internal.service.UserStatusAudit;
import com.library.userservice.security.AdminActor;
import com.library.userservice.security.AdminActorAuthenticator;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalUserStatusServiceTests {
    final UserRepository users = mock(UserRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final UserSessionRepository sessions = mock(UserSessionRepository.class);
    final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    final AdminActorAuthenticator actors = mock(AdminActorAuthenticator.class);
    final UserStatusAudit audit = mock(UserStatusAudit.class);
    final Clock clock = mock(Clock.class);
    final InternalUserStatusService service = new InternalUserStatusService(users, roles, sessions, tokens, actors, audit, clock);
    final AdminActor actor = new AdminActor(9, UUID.randomUUID(), "private-fixture-token");
    final Instant now = Instant.parse("2026-11-17T00:00:00Z");
    User target;

    @BeforeEach void setup() {
        TransactionSynchronizationManager.initSynchronization();
        target = new User(); target.setStatus(UserStatus.ACTIVE);
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(9)).thenReturn(Optional.of(new User()));
        when(clock.instant()).thenReturn(now);
        when(roles.findNamesByUserId(3)).thenReturn(List.of("READER"));
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void locksInAscendingOrderRechecksThenRevokesAndAuditsOnlyAfterCommit() {
        var result = service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.BANNED));
        assertThat(result.id()).isEqualTo(3); assertThat(result.status()).isEqualTo(UserStatus.BANNED);
        var order = inOrder(users, clock, actors, tokens, sessions);
        order.verify(users).findByIdForUpdate(3); order.verify(users).findByIdForUpdate(9);
        order.verify(clock).instant(); order.verify(actors).authenticateAt(actor.accessToken(), now);
        order.verify(users).save(target); order.verify(tokens).revokeActiveByUser(3, now);
        order.verify(sessions).revokeActiveByUser(3, now, "ACCOUNT_BANNED");
        verifyNoInteractions(audit);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(audit).record(any(UUID.class), eq(9), eq(3), eq(UserStatus.ACTIVE), eq(UserStatus.BANNED), eq(now));
        assertThat(actor.toString()).doesNotContain(actor.accessToken());
    }

    @Test void reactivationAndSameStatusNeverRestoreOrRevokeTokens() {
        target.setStatus(UserStatus.BANNED);
        service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.ACTIVE));
        verify(users).save(target); verifyNoInteractions(tokens, sessions);
        clearInvocations(users);
        service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.ACTIVE));
        verify(users, never()).save(any()); verifyNoInteractions(tokens, sessions);
    }

    @Test void rejectsAdminTargetsEvenWhenAlreadyBannedAndLocksSelfOnce() {
        when(roles.findNamesByUserId(3)).thenReturn(List.of("ADMIN", "READER"));
        target.setStatus(UserStatus.BANNED);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.BANNED)));
        when(roles.findNamesByUserId(9)).thenReturn(List.of("ADMIN"));
        clearInvocations(users);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.updateStatus(actor, 9, new UserStatusRequest(UserStatus.BANNED)));
        verify(users, times(1)).findByIdForUpdate(9); verifyNoInteractions(tokens, sessions, audit);
    }

    @Test void actorFailureTakesPrecedenceOverMissingTargetAndDoesNotWrite() {
        when(users.findByIdForUpdate(3)).thenReturn(Optional.empty());
        doThrow(new ApplicationException(ErrorCode.ADMIN_ACTOR_INVALID)).when(actors).authenticateAt(anyString(), any());
        assertError(ErrorCode.ADMIN_ACTOR_INVALID, () -> service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.ACTIVE)));
        doReturn(actor).when(actors).authenticateAt(anyString(), any());
        assertError(ErrorCode.USER_NOT_FOUND, () -> service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.ACTIVE)));
        verify(users, never()).save(any()); verifyNoInteractions(tokens, sessions, audit);
    }

    @Test void failuresDoNotRegisterSuccessAndAuditFailureCannotUndoCommit() {
        doThrow(new IllegalStateException("private-fixture")).when(tokens).revokeActiveByUser(3, now);
        assertThatThrownBy(() -> service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.BANNED))).isInstanceOf(IllegalStateException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        target.setStatus(UserStatus.ACTIVE); reset(tokens);
        service.updateStatus(actor, 3, new UserStatusRequest(UserStatus.ACTIVE));
        doThrow(new IllegalStateException("private-fixture")).when(audit).record(any(), anyInt(), anyInt(), any(), any(), any());
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit())).doesNotThrowAnyException();
    }

    private void assertError(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
