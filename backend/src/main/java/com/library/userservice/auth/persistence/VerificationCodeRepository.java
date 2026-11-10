package com.library.userservice.auth.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface VerificationCodeRepository extends Repository<VerificationCode, Integer> {
    <S extends VerificationCode> S save(S entity);
    Optional<VerificationCode> findById(Integer id);
    List<VerificationCode> findByEmailAndPurposeOrderByCreatedAtDescIdDesc(String email, VerificationPurpose purpose);
}

