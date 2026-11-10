package com.library.userservice.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record IntrospectionActiveResponse(String sub, UUID sid, String token_type, String iss, List<String> aud, long exp, long iat, List<String> roles) {
    public IntrospectionActiveResponse {
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(sid, "sid");
        Objects.requireNonNull(token_type, "token_type");
        Objects.requireNonNull(iss, "iss");
        aud = List.copyOf(aud);
        roles = List.copyOf(roles);
        if (sub.isEmpty() || aud.isEmpty() || !"Bearer".equals(token_type)) throw new IllegalArgumentException("Introspection không đúng hợp đồng");
    }
    @JsonProperty("active") public boolean active() { return true; }
}

