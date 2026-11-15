package com.library.userservice.internal.service;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import com.library.userservice.internal.dto.InternalUserSummary;
import com.library.userservice.user.persistence.RoleRepository;
import com.library.userservice.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserService {
    private final UserRepository users;
    private final RoleRepository roles;

    public InternalUserService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public InternalUserSummary getUser(int id) {
        var user = users.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        return new InternalUserSummary(user.getId(), user.getUserName(), user.getFullName(), user.getStatus(),
                roles.findNamesByUserId(id));
    }
}
