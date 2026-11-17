package com.library.userservice.security;

import java.util.UUID;

/** Chỉ được tạo sau xác thực; không để token xuất hiện trong log. */
public record AdminActor(int userId, UUID sessionId, String accessToken) {
    @Override public String toString() { return "AdminActor[credentials=REDACTED]"; }
}
