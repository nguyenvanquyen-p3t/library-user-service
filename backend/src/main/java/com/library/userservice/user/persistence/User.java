package com.library.userservice.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Ánh xạ schema Flyway; không chứa luồng nghiệp vụ hoặc dữ liệu response. */
@Entity
@Table(name = "\"Users\"")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"", nullable = false)
    private Integer id;

    @Column(name = "\"UserName\"", nullable = false, length = 50)
    private String userName;

    @Column(name = "\"PasswordHash\"", nullable = true, length = 255)
    private String passwordHash;

    @Column(name = "\"Email\"", nullable = false, length = 255)
    private String email;

    @Column(name = "\"FullName\"", nullable = false, length = 150)
    private String fullName;

    @Column(name = "\"PhoneNumber\"", nullable = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "\"Status\"", nullable = false, length = 20)
    private UserStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"CreatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "\"UpdatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public User() {}

    public Integer getId() { return id; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

