package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.GoogleLinkedResult;
import com.library.userservice.auth.dto.GoogleLoginResult;
import com.library.userservice.auth.dto.GoogleRegisterRequest;
import com.library.userservice.auth.dto.GoogleRegistrationRequiredResult;
import com.library.userservice.auth.dto.GoogleRegistrationResult;
import com.library.userservice.auth.persistence.UserIdentity;
import com.library.userservice.auth.persistence.UserIdentityRepository;
import com.library.userservice.common.api.ApiResponse;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.api.SuccessCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.common.validation.Input;
import com.library.userservice.common.validation.InputRules;
import com.library.userservice.common.validation.Normalization;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserRole;
import com.library.userservice.user.persistence.UserRoleId;
import com.library.userservice.user.persistence.UserRoleRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class GoogleAuthTransactions {
    private static final String GOOGLE = "GOOGLE";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final LoginSessionIssuer sessionIssuer;
    private final GoogleIdTokenVerifier verifier;
    private final GoogleRegistrationToken registrationTokens;

    GoogleAuthTransactions(JdbcTemplate jdbc, UserRepository users, UserIdentityRepository identities,
            RoleRepository roles, UserRoleRepository userRoles, LoginSessionIssuer sessionIssuer,
            GoogleIdTokenVerifier verifier, GoogleRegistrationToken registrationTokens) {
        this.jdbc = jdbc;
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.userRoles = userRoles;
        this.sessionIssuer = sessionIssuer;
        this.verifier = verifier;
        this.registrationTokens = registrationTokens;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApiResponse<?> authenticate(GoogleIdTokenVerifier.Identity google) {
        lockSubject(google.subject());
        var identity = identities.findByProviderAndProviderUserId(GOOGLE, google.subject());
        if (identity.isPresent()) {
            var user = users.findByIdForUpdate(identity.orElseThrow().getUserId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.GOOGLE_TOKEN_INVALID));
            verifier.requireUnexpired(google.expiresAt());
            requireActive(user);
            return ApiResponse.success(SuccessCode.GOOGLE_LOGIN_SUCCESS,
                    new GoogleLoginResult(sessionIssuer.issue(user.getId())));
        }

        String email = Normalization.identifier(google.email());
        if (!google.emailVerified() || InputRules.check(email, Input.Kind.EMAIL, true) != null)
            throw new ApplicationException(ErrorCode.GOOGLE_TOKEN_INVALID);
        var local = users.findByEmailForUpdate(email);
        verifier.requireUnexpired(google.expiresAt());
        if (local.isPresent()) {
            var user = local.orElseThrow();
            if ((!email.endsWith("@gmail.com") && !google.hostedDomain()) || user.getPasswordHash() == null
                    || identities.findByUserId(user.getId()).stream().anyMatch(link -> GOOGLE.equals(link.getProvider())))
                throw new ApplicationException(ErrorCode.GOOGLE_TOKEN_INVALID);
            requireActive(user);
            saveIdentity(user.getId(), google.subject());
            return ApiResponse.success(SuccessCode.GOOGLE_ACCOUNT_LINKED,
                    new GoogleLinkedResult(sessionIssuer.issue(user.getId())));
        }

        String name = Normalization.fullName(google.fullName());
        String suggestedUserName = Normalization.identifier(email.substring(0, email.lastIndexOf('@')));
        return ApiResponse.success(SuccessCode.GOOGLE_REGISTRATION_REQUIRED,
                new GoogleRegistrationRequiredResult(email, suggestion(name, Input.Kind.FULL_NAME),
                        suggestion(suggestedUserName, Input.Kind.USERNAME),
                        registrationTokens.issue(google.subject(), email)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GoogleRegistrationResult register(GoogleRegistrationToken.Registration registration,
            GoogleRegisterRequest request) {
        lockSubject(registration.subject());
        registrationTokens.requireUnexpired(registration.expiresAt());
        if (identities.findByProviderAndProviderUserId(GOOGLE, registration.subject()).isPresent())
            throw new ApplicationException(ErrorCode.GOOGLE_IDENTITY_ALREADY_REGISTERED);
        if (users.findByEmail(registration.email()).isPresent())
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        if (users.findByUserName(request.userName()).isPresent())
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS);

        var reader = roles.findByName("READER").orElseThrow(() -> new ApplicationException(ErrorCode.INTERNAL_ERROR));
        var user = new User();
        user.setUserName(request.userName());
        user.setEmail(registration.email());
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        var saved = users.save(user);
        saveIdentity(saved.getId(), registration.subject());
        userRoles.save(new UserRole(new UserRoleId(saved.getId(), reader.getId())));

        var profile = new UserProfile(saved.getId(), saved.getUserName(), saved.getEmail(), saved.getFullName(),
                saved.getPhoneNumber(), saved.getStatus(), List.of(reader.getName()), false, List.of(GOOGLE));
        return new GoogleRegistrationResult(profile, sessionIssuer.issue(saved.getId()));
    }

    private void lockSubject(String subject) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSetExtractor<Void>) result -> null, "library-user-service:google:" + subject);
    }

    private void saveIdentity(int userId, String subject) {
        var identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(GOOGLE);
        identity.setProviderUserId(subject);
        identities.save(identity);
    }

    private static void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) throw new ApplicationException(ErrorCode.ACCOUNT_BANNED);
    }

    private static String suggestion(String value, Input.Kind kind) {
        return InputRules.check(value, kind, true) == null ? value : null;
    }
}
