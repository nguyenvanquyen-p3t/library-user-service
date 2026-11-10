package com.library.userservice.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import com.library.userservice.user.persistence.UserStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserProfile(int id, String userName, String email, String fullName, String phoneNumber, UserStatus status, List<String> roles, boolean hasPassword, List<String> linkedProviders) {
    public UserProfile {
        Objects.requireNonNull(userName, "userName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(status, "status");
        roles = List.copyOf(roles);
        linkedProviders = List.copyOf(linkedProviders);
        if (id <= 0) throw new IllegalArgumentException("ID phải dương");
    }
}

