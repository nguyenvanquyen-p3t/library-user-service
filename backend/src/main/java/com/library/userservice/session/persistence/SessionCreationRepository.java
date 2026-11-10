package com.library.userservice.session.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tham gia transaction caller; CreatedAt/LastSeenAt tiếp tục do database sinh. */
@Repository
public class SessionCreationRepository {
    private final JdbcTemplate jdbc;
    public SessionCreationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, int userId) {
        jdbc.update("""
                INSERT INTO "UserSessions" ("Id", "UserId", "ExpiresAt")
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '2592000 seconds')
                """, id, userId);
    }
}
