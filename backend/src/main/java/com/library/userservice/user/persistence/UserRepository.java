package com.library.userservice.user.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface UserRepository extends Repository<User, Integer> {
    <S extends User> S save(S entity);
    Optional<User> findById(Integer id);
    Optional<User> findByUserName(String userName);
    Optional<User> findByEmail(String email);
}

