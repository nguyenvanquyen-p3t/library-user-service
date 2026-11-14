package com.library.userservice.session.persistence;

import java.util.Optional;
import java.util.List;
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

    @Query("select count(s) from UserSession s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now")
    long countActiveByUserId(@Param("userId") Integer userId, @Param("now") Instant now);

    // OFFSET bigint tránh giới hạn int của JPA khi page hợp lệ nhưng rất lớn.
    @Query(value = """
            select s.* from "UserSessions" s
            where s."UserId" = :userId and s."RevokedAt" is null and s."ExpiresAt" > :now
            order by s."CreatedAt" desc, s."Id" asc limit :size offset :offset
            """, nativeQuery = true)
    List<UserSession> findActiveWindow(@Param("userId") Integer userId, @Param("now") Instant now,
            @Param("size") int size, @Param("offset") long offset);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revocationReason = :reason
            where s.userId = :userId and s.id <> :currentSid and s.revokedAt is null and s.expiresAt > :now
            """)
    int revokeActiveOthers(@Param("userId") Integer userId, @Param("currentSid") UUID currentSid,
            @Param("now") Instant now, @Param("reason") String reason);

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

