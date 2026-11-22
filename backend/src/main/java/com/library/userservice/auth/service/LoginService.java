package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.LoginRequest;
import com.library.userservice.auth.dto.TokenPair;
import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.security.PasswordService;
import com.library.userservice.user.persistence.UserRepository;
import com.library.userservice.user.persistence.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordService passwords;
    private final LoginSessionIssuer sessionIssuer;

    public LoginService(UserRepository users, PasswordService passwords, LoginSessionIssuer sessionIssuer) {
        this.users = users;
        this.passwords = passwords;
        this.sessionIssuer = sessionIssuer;
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        var candidate = request.identifier().contains("@") ? users.findByEmailForUpdate(request.identifier())
                : users.findByUserNameForUpdate(request.identifier());
        if (candidate.isEmpty()) {
            passwords.matches(request.password(), null);
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        var user = candidate.orElseThrow();
        if (user.getPasswordHash() == null) throw new ApplicationException(ErrorCode.PASSWORD_NOT_CONFIGURED);
        if (!passwords.matches(request.password(), user.getPasswordHash())) throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        if (user.getStatus() != UserStatus.ACTIVE) throw new ApplicationException(ErrorCode.ACCOUNT_BANNED);
        return sessionIssuer.issue(user.getId());
    }
}
