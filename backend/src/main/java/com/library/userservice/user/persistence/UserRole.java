package com.library.userservice.user.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"UserRoles\"")
public class UserRole {
    @EmbeddedId
    private UserRoleId id;
    protected UserRole() {}
    public UserRole(UserRoleId id) { this.id = id; }
    public UserRoleId getId() { return id; }
}

