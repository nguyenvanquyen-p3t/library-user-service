package com.library.userservice.internal.service;

import com.library.userservice.user.persistence.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserStatusAudit {
    public void record(UUID requestId, int actorId, int targetId, UserStatus before, UserStatus after, Instant at) {
        LoggerFactory.getLogger(UserStatusAudit.class).info(
                "event=USER_STATUS_UPDATED service=UNVERIFIED_INTERNAL_CALLER serviceAuthenticated=false actor={} target={} before={} after={} at={} requestId={}",
                actorId, targetId, before, after, at, requestId);
    }
}
