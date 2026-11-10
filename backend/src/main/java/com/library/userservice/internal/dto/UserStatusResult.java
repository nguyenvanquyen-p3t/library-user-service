package com.library.userservice.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import com.library.userservice.user.persistence.UserStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserStatusResult(int id, UserStatus status) {
    public UserStatusResult {
        Objects.requireNonNull(status, "status");
        if (id <= 0) throw new IllegalArgumentException("ID phải dương");
    }
}

