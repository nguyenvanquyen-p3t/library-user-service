package com.library.userservice.security;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UserPrincipal(int userId, UUID sessionId, List<String> roles) implements Principal {
    public UserPrincipal {
        if (userId <= 0) throw new IllegalArgumentException("User ID không hợp lệ");
        Objects.requireNonNull(sessionId);
        roles = List.copyOf(roles);
    }
    @Override public String getName() { return Integer.toString(userId); }
}
