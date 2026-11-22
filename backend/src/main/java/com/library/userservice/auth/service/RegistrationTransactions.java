package com.library.userservice.auth.service;

import static com.library.userservice.auth.persistence.VerificationPurpose.REGISTER;

import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.dto.RegisterRequest;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.user.dto.UserProfile;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.User;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserRole;
import com.library.userservice.user.persistence.UserRoleId;
import com.library.userservice.user.persistence.UserRoleRepository;
import com.library.userservice.user.persistence.UserStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationTransactions {
    private final EmailOtpService otp;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordService passwords;

    public RegistrationTransactions(EmailOtpService otp, UserRepository users, RoleRepository roles,
            UserRoleRepository userRoles, PasswordService passwords) {
        this.otp = otp;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwords = passwords;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issue(RegisterCodeRequest request) {
        otp.requireConfigured();
        otp.lock(request.email(), REGISTER);
        var now = otp.now();
        if (users.findByUserName(request.userName()).isPresent())
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS);
        if (users.findByEmail(request.email()).isPresent())
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);

        return otp.issue(request.email(), REGISTER, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = EmailOtpService.WrongOtpAttempt.class)
    public UserProfile register(RegisterRequest request) {
        otp.lock(request.email(), REGISTER);
        var now = otp.now();
        var code = otp.verify(request.email(), REGISTER, request.verificationCode(), now);

        var reader = roles.findByName("READER").orElseThrow(() -> new ApplicationException(ErrorCode.INTERNAL_ERROR));
        var user = new User();
        user.setUserName(request.userName());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        var saved = users.save(user);
        userRoles.save(new UserRole(new UserRoleId(saved.getId(), reader.getId())));
        code.setUsedAt(now);
        return new UserProfile(saved.getId(), saved.getUserName(), saved.getEmail(), saved.getFullName(),
                saved.getPhoneNumber(), saved.getStatus(), List.of(reader.getName()), true, List.of());
    }
}
