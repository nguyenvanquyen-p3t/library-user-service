package com.library.userservice.auth.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface RefreshTokenRepository extends Repository<RefreshToken, Integer> {
    <S extends RefreshToken> S save(S entity);
    Optional<RefreshToken> findById(Integer id);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findBySessionId(UUID sessionId);
}

