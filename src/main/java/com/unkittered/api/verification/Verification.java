package com.unkittered.api.verification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A user's photo-verification request. One per user (re-submit replaces it). */
@Entity
@Table(name = "verifications")
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "selfie_url", nullable = false)
    private String selfieUrl;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected Verification() { }

    public Verification(UUID userId, String selfieUrl) {
        this.userId = userId;
        this.selfieUrl = selfieUrl;
    }

    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public void setSelfieUrl(String selfieUrl) { this.selfieUrl = selfieUrl; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
}
