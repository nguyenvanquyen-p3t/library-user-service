package com.library.userservice.auth.persistence;

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
@Table(name = "\"VerificationCodes\"")
public class VerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"", nullable = false)
    private Integer id;

    @Column(name = "\"Email\"", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "\"Purpose\"", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "\"CodeHash\"", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "\"ExpiresAt\"", nullable = false)
    private Instant expiresAt;

    @Column(name = "\"UsedAt\"", nullable = true)
    private Instant usedAt;

    @Column(name = "\"AttemptCount\"", nullable = false)
    private Integer attemptCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "\"CreatedAt\"", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public VerificationCode() {}

    public Integer getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public VerificationPurpose getPurpose() { return purpose; }
    public void setPurpose(VerificationPurpose purpose) { this.purpose = purpose; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
}

