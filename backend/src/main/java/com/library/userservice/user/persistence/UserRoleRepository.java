package com.library.userservice.user.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface UserRoleRepository extends Repository<UserRole, UserRoleId> {
    <S extends UserRole> S save(S entity);
    Optional<UserRole> findById(UserRoleId id);
    List<UserRole> findByIdUserId(Integer userId);
}

