package com.unkittered.api.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Medical proof for the verified sterilisation badge. One per member. */
@Entity
@Table(name = "sterilisation_proofs")
public class SterilisationProof {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** Cleared once reviewed: proof is never kept longer than needed. */
    @Column(name = "document_url", nullable = false)
    private String documentUrl;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected SterilisationProof() { }

    public SterilisationProof(UUID userId, String documentUrl) {
        this.userId = userId;
        this.documentUrl = documentUrl;
    }

    public UUID getUserId() { return userId; }
    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
