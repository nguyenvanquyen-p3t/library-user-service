package com.library.userservice.user;

import com.library.userservice.auth.persistence.UserIdentity;
import com.library.userservice.auth.persistence.UserIdentityRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.persistence.UserSession;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.ProfilePatchRequest;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import com.library.userservice.user.service.UserProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserIdentityRepository identities = mock(UserIdentityRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final Clock clock = mock(Clock.class);
    private final UserProfileService profiles = new UserProfileService(users, roles, identities, sessions, clock);
    private final Instant now = Instant.parse("2026-11-12T00:00:00Z");
    private final UserPrincipal principal = new UserPrincipal(3, UUID.randomUUID(), List.of("OLD_ROLE"));
    private User user;
    private UserSession session;

    @BeforeEach void setup() {
        user = new User(); ReflectionTestUtils.setField(user, "id", 3);
        user.setUserName("reader"); user.setEmail("reader@example.com"); user.setFullName("Reader");
        user.setStatus(UserStatus.ACTIVE); user.setPhoneNumber("12345678");
        session = new UserSession(); session.setUserId(3); session.setExpiresAt(now.plusSeconds(60));
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(now);
        when(roles.findNamesByUserId(3)).thenReturn(List.of("READER", "ADMIN"));
        when(identities.findByUserId(3)).thenReturn(List.of());
    }

    @Test void readsCurrentRolesProvidersAndPasswordPresenceWithoutWriting() {
        var google = new UserIdentity(); google.setProvider("GOOGLE");
        when(identities.findByUserId(3)).thenReturn(List.of(google));
        var result = profiles.getProfile(principal);
        assertThat(result.roles()).containsExactly("ADMIN", "READER");
        assertThat(result.linkedProviders()).containsExactly("GOOGLE");
        assertThat(result.hasPassword()).isFalse();
        user.setPasswordHash("fixture-hash"); user.setPhoneNumber(null);
        when(roles.findNamesByUserId(3)).thenReturn(List.of());
        when(identities.findByUserId(3)).thenReturn(List.of());
        result = profiles.getProfile(principal);
        assertThat(result.roles()).isEmpty(); assertThat(result.linkedProviders()).isEmpty();
        assertThat(result.phoneNumber()).isNull(); assertThat(result.hasPassword()).isTrue();
        verify(users, never()).save(any()); verifyNoInteractions(sessions, clock);
    }

    @Test void missingUserOnReadIsInactive() {
        when(users.findById(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> profiles.getProfile(principal)).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SESSION_INACTIVE));
    }

    @Test void appliesOnlyPresentFieldsAndReadsTimeAfterLock() {
        var request = new ProfilePatchRequest(); request.setFullName("  Nguyễn Văn A  ");
        var result = profiles.updateProfile(principal, request);
        assertThat(result.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.phoneNumber()).isEqualTo("12345678");
        var order = inOrder(users, clock, sessions);
        order.verify(users).findByIdForUpdate(3); order.verify(clock).instant();
        order.verify(sessions).findById(principal.sessionId()); order.verify(users).save(user);
        verify(users, never()).findById(anyInt());
        request = new ProfilePatchRequest(); request.setPhoneNumber(null);
        assertThat(profiles.updateProfile(principal, request).phoneNumber()).isNull();
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(user.getUserName()).isEqualTo("reader"); assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(sessions, never()).save(any());
    }

    @Test void rejectsMissingOrBannedUserBeforeWriting() {
        when(users.findByIdForUpdate(3)).thenReturn(Optional.empty()); assertInactive();
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(user));
        user.setStatus(UserStatus.BANNED); assertInactive();
        verifyNoInteractions(sessions);
    }

    @Test void rejectsMissingWrongOwnerRevokedAndExpiredSessionIncludingExactBoundary() {
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.empty()); assertInactive();
        when(sessions.findById(principal.sessionId())).thenReturn(Optional.of(session));
        session.setUserId(4); assertInactive(); session.setUserId(3);
        session.setRevokedAt(now); assertInactive(); session.setRevokedAt(null);
        session.setExpiresAt(now); assertInactive();
        session.setExpiresAt(now.minusSeconds(1)); assertInactive();
        assertThat(user.getFullName()).isEqualTo("Reader");
    }

    @Test void samplesClockAfterWaitingForUserLock() {
        when(users.findByIdForUpdate(3)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(session.getExpiresAt()); return Optional.of(user);
        });
        assertInactive();
    }

    @Test void responseAssemblyFailurePropagatesToTransactionalBoundary() {
        when(identities.findByUserId(3)).thenThrow(new IllegalStateException("fixture-mapping-failure"));
        var request = new ProfilePatchRequest(); request.setFullName("Changed");
        assertThatThrownBy(() -> profiles.updateProfile(principal, request)).isInstanceOf(IllegalStateException.class);
        verify(users).save(user);
    }

    private void assertInactive() {
        var request = new ProfilePatchRequest(); request.setFullName("Changed");
        assertThatThrownBy(() -> profiles.updateProfile(principal, request)).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SESSION_INACTIVE));
        verify(users, never()).save(any());
    }
}
