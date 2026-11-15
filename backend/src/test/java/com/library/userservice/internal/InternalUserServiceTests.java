package com.library.userservice.internal;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.InternalUserSummary;
import com.library.userservice.internal.service.InternalUserService;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalUserServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final InternalUserService service = new InternalUserService(users, roles);

    @Test void mapsActiveAndBannedUsersWithCurrentDatabaseRoles() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(3);
        when(user.getUserName()).thenReturn("reader");
        when(user.getFullName()).thenReturn("Nguyễn Văn A");
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(roles.findNamesByUserId(3)).thenReturn(List.of("ADMIN", "READER"), List.of());
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE, UserStatus.BANNED);

        assertThat(service.getUser(3)).isEqualTo(new InternalUserSummary(3, "reader", "Nguyễn Văn A",
                UserStatus.ACTIVE, List.of("ADMIN", "READER")));
        assertThat(service.getUser(3)).isEqualTo(new InternalUserSummary(3, "reader", "Nguyễn Văn A",
                UserStatus.BANNED, List.of()));
        verify(users, never()).findByIdForUpdate(anyInt());
        verify(users, never()).save(any());
    }

    @Test void missingUserReturnsNotFoundBeforeReadingRoles() {
        when(users.findById(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUser(3)).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(roles);
    }

    @Test void repositoryFailuresPropagateFromUserAndRoleReads() {
        var failure = new DataAccessResourceFailureException("private-fixture");
        when(users.findById(3)).thenThrow(failure);
        assertThatThrownBy(() -> service.getUser(3)).isSameAs(failure);

        var user = mock(User.class);
        when(user.getId()).thenReturn(3);
        doReturn(Optional.of(user)).when(users).findById(3);
        when(roles.findNamesByUserId(3)).thenThrow(failure);
        assertThatThrownBy(() -> service.getUser(3)).isSameAs(failure);
    }
}
