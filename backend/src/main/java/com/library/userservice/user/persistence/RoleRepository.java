package com.library.userservice.user.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface RoleRepository extends Repository<Role, Integer> {
    <S extends Role> S save(S entity);
    Optional<Role> findById(Integer id);
    Optional<Role> findByName(String name);
}

