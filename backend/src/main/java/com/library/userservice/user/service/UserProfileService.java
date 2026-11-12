package com.library.userservice.user.service;

import com.library.userservice.auth.persistence.UserIdentity;
import com.library.userservice.auth.persistence.UserIdentityRepository;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.session.persistence.UserSessionRepository;
import com.library.userservice.user.dto.ProfilePatchRequest;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserIdentityRepository identities;
    private final UserSessionRepository sessions;
    private final Clock clock;

    public UserProfileService(UserRepository users, RoleRepository roles, UserIdentityRepository identities,
            UserSessionRepository sessions, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.identities = identities;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public UserProfile getProfile(UserPrincipal principal) {
        return profile(users.findById(principal.userId()).orElseThrow(UserProfileService::inactive));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserProfile updateProfile(UserPrincipal principal, ProfilePatchRequest request) {
        // Không dùng entity đã đọc ở Bearer; khóa cùng user với login/refresh/logout.
        var user = users.findByIdForUpdate(principal.userId()).orElseThrow(UserProfileService::inactive);
        var now = clock.instant();
        if (user.getStatus() != UserStatus.ACTIVE) throw inactive();
        var session = sessions.findById(principal.sessionId()).orElseThrow(UserProfileService::inactive);
        if (!Integer.valueOf(principal.userId()).equals(session.getUserId()) || session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())) throw inactive();

        if (request.hasFullName()) user.setFullName(request.getFullName());
        if (request.hasPhoneNumber()) user.setPhoneNumber(request.getPhoneNumber());
        user = users.save(user);
        // Dựng DTO trong transaction; cả lỗi dựng response và lỗi COMMIT đều không trả thành công.
        return profile(user);
    }

    private UserProfile profile(User user) {
        var currentRoles = roles.findNamesByUserId(user.getId()).stream().sorted().toList();
        var providers = identities.findByUserId(user.getId()).stream().map(UserIdentity::getProvider)
                .distinct().sorted().toList();
        return new UserProfile(user.getId(), user.getUserName(), user.getEmail(), user.getFullName(),
                user.getPhoneNumber(), user.getStatus(), currentRoles, user.getPasswordHash() != null, providers);
    }

    private static ApplicationException inactive() { return new ApplicationException(ErrorCode.SESSION_INACTIVE); }
}
