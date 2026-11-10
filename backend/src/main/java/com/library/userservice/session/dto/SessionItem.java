package com.library.userservice.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.library.userservice.session.persistence.ClientType;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SessionItem(UUID id, String deviceName, ClientType clientType, Instant createdAt, Instant lastSeenAt, Instant expiresAt, boolean isCurrent) {
    public SessionItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}

