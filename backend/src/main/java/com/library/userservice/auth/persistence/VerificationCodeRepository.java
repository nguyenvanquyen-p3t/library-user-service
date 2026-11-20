package com.library.userservice.auth.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface VerificationCodeRepository extends Repository<VerificationCode, Integer> {
    <S extends VerificationCode> S save(S entity);
    Optional<VerificationCode> findById(Integer id);
    List<VerificationCode> findByEmailAndPurposeOrderByCreatedAtDescIdDesc(String email, VerificationPurpose purpose);
    List<VerificationCode> findFirst5ByEmailAndPurposeOrderByCreatedAtDescIdDesc(
            String email, VerificationPurpose purpose);
    Optional<VerificationCode> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

    @Modifying
    @Query(value = """
            INSERT INTO "VerificationCodes" ("Email", "Purpose", "CodeHash", "CreatedAt", "ExpiresAt")
            VALUES (:email, :purpose, :hash, :createdAt, :expiresAt)
            """, nativeQuery = true)
    void insertIssuedCode(@Param("email") String email, @Param("purpose") String purpose, @Param("hash") String hash,
            @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);
}

