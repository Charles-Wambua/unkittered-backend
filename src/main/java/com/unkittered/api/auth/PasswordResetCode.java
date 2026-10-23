package com.unkittered.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A pending password reset: one per account, hashed, short-lived. */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetCode() { }

    public PasswordResetCode(UUID userId, String codeHash, Instant expiresAt, Instant now) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }

    public void recordFailedAttempt() { attempts++; }
}
