package com.library.userservice.session.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Ánh xạ schema Flyway; không chứa luồng nghiệp vụ hoặc dữ liệu response. */
@Entity
@Table(name = "\"UserSessions\"")
public class UserSession {
    @Id
    @Column(name = "\"Id\"", nullable = false)
    private UUID id;

    @Column(name = "\"UserId\"", nullable = false)
    private Integer userId;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"CreatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"ExpiresAt\"", nullable = false)
    private Instant expiresAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"LastSeenAt\"", nullable = false, insertable = false)
    private Instant lastSeenAt;

    @Column(name = "\"RevokedAt\"", nullable = true)
    private Instant revokedAt;

    @Column(name = "\"RevocationReason\"", nullable = true, length = 50)
    private String revocationReason;

    @Column(name = "\"DeviceName\"", nullable = true, length = 150)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "\"ClientType\"", nullable = true, length = 20)
    private ClientType clientType;

    @Column(name = "\"UserAgent\"", nullable = true, length = 1024)
    private String userAgent;

    public UserSession() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}

