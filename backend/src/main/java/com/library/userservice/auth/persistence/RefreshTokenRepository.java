package com.library.userservice.auth.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface RefreshTokenRepository extends Repository<RefreshToken, Integer> {
    <S extends RefreshToken> S save(S entity);
    Optional<RefreshToken> findById(Integer id);
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Projection trước khóa user: không để entity token cũ trong persistence context.
    @Query("select t.userId from RefreshToken t where t.tokenHash = :hash")
    Optional<Integer> findOwnerByTokenHash(@Param("hash") String hash);

    List<RefreshToken> findBySessionId(UUID sessionId);

    // Gọi dưới khóa user và trước khi thu hồi phiên, dùng cùng now cho cả hai bulk update.
    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now
            and t.sessionId in (select s.id from UserSession s
                where s.userId = :userId and s.id <> :currentSid
                and s.revokedAt is null and s.expiresAt > :now)
            """)
    int revokeActiveByOtherActiveSessions(@Param("userId") Integer userId, @Param("currentSid") UUID currentSid,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now
            """)
    int revokeActiveByUser(@Param("userId") Integer userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.sessionId = :sessionId and t.userId = :userId
            and t.revokedAt is null and t.expiresAt > :now
            """)
    int revokeActiveBySession(@Param("sessionId") UUID sessionId, @Param("userId") Integer userId,
            @Param("now") Instant now);
}

