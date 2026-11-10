package com.library.userservice.user.persistence;

import java.util.Optional;
import java.util.List;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ lưu/tra cứu; caller chịu trách nhiệm transaction và quy tắc nghiệp vụ. */
public interface RoleRepository extends Repository<Role, Integer> {
    <S extends Role> S save(S entity);
    Optional<Role> findById(Integer id);
    Optional<Role> findByName(String name);

    @Query("select r.name from Role r, UserRole ur where ur.id.roleId = r.id and ur.id.userId = :userId order by r.name")
    List<String> findNamesByUserId(@Param("userId") Integer userId);
}

