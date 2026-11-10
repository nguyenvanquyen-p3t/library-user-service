package com.library.userservice.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Ánh xạ schema Flyway; không chứa luồng nghiệp vụ hoặc dữ liệu response. */
@Entity
@Table(name = "\"RefreshTokens\"")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"", nullable = false)
    private Integer id;

    @Column(name = "\"UserId\"", nullable = false)
    private Integer userId;

    @Column(name = "\"SessionId\"", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "\"TokenHash\"", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "\"ExpiresAt\"", nullable = false)
    private Instant expiresAt;

    @Column(name = "\"RevokedAt\"", nullable = true)
    private Instant revokedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"CreatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "\"ReplacedByTokenId\"", nullable = true)
    private Integer replacedByTokenId;

    public RefreshToken() {}

    public Integer getId() { return id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { if (this.sessionId != null && !this.sessionId.equals(sessionId)) throw new IllegalStateException("Không được thay phiên của token"); this.sessionId = sessionId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getReplacedByTokenId() { return replacedByTokenId; }
    public void setReplacedByTokenId(Integer replacedByTokenId) { this.replacedByTokenId = replacedByTokenId; }
}

