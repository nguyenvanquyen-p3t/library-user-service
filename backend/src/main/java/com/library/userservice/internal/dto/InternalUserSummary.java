package com.library.userservice.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import com.library.userservice.user.persistence.UserStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record InternalUserSummary(int id, String userName, String fullName, UserStatus status, List<String> roles) {
    public InternalUserSummary {
        Objects.requireNonNull(userName, "userName");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(status, "status");
        roles = List.copyOf(roles);
        if (id <= 0) throw new IllegalArgumentException("ID phải dương");
    }
}

