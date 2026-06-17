package com.unkittered.api.safety;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A user report submitted for moderation review. */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_id", nullable = false)
    private UUID reportedId;

    @Column(nullable = false)
    private String reason;

    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Report() { }

    public Report(UUID reporterId, UUID reportedId, String reason, String details) {
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.reason = reason;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public UUID getReportedId() { return reportedId; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
