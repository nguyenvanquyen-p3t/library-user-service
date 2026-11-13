package com.library.userservice.session.persistence;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface UserSessionRepository extends Repository<UserSession, UUID> {
    <S extends UserSession> S save(S entity);
    Optional<UserSession> findById(UUID id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revocationReason = :reason
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            """)
    int revokeActiveByUser(@Param("userId") Integer userId, @Param("now") Instant now,
            @Param("reason") String reason);

    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.createdAt desc, s.id asc
            """)
    Page<UserSession> findActiveByUserId(@Param("userId") Integer userId, @Param("now") Instant now, Pageable pageable);
}

