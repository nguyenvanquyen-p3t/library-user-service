package com.library.userservice.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Ánh xạ schema Flyway; không chứa luồng nghiệp vụ hoặc dữ liệu response. */
@Entity
@Table(name = "\"UserIdentities\"")
public class UserIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"", nullable = false)
    private Integer id;

    @Column(name = "\"UserId\"", nullable = false)
    private Integer userId;

    @Column(name = "\"Provider\"", nullable = false, length = 30)
    private String provider;

    @Column(name = "\"ProviderUserId\"", nullable = false, length = 255)
    private String providerUserId;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"CreatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public UserIdentity() {}

    public Integer getId() { return id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderUserId() { return providerUserId; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }
    public Instant getCreatedAt() { return createdAt; }
}

