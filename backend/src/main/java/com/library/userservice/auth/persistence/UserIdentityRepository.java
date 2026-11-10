package com.library.userservice.auth.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface UserIdentityRepository extends Repository<UserIdentity, Integer> {
    <S extends UserIdentity> S save(S entity);
    Optional<UserIdentity> findById(Integer id);
    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<UserIdentity> findByUserId(Integer userId);
}

