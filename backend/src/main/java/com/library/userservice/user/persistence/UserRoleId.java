package com.library.userservice.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserRoleId implements Serializable {
    @Column(name = "\"UserId\"", nullable = false)
    private Integer userId;
    @Column(name = "\"RoleId\"", nullable = false)
    private Integer roleId;

    protected UserRoleId() {}
    public UserRoleId(Integer userId, Integer roleId) { this.userId = userId; this.roleId = roleId; }
    public Integer getUserId() { return userId; }
    public Integer getRoleId() { return roleId; }
    @Override public boolean equals(Object other) {
        return other instanceof UserRoleId that && Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }
    @Override public int hashCode() { return Objects.hash(userId, roleId); }
}

