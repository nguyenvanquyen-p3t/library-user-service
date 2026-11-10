package com.library.userservice.user.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface UserRepository extends Repository<User, Integer> {
    <S extends User> S save(S entity);
    Optional<User> findById(Integer id);
    Optional<User> findByUserName(String userName);
    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userName = :userName")
    Optional<User> findByUserNameForUpdate(@Param("userName") String userName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}

