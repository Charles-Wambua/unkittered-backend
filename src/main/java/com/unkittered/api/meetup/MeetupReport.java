package com.unkittered.api.meetup;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A moderation report against a meetup. */
@Entity
@Table(name = "meetup_reports")
public class MeetupReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meetup_id", nullable = false)
    private UUID meetupId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MeetupReport() { }

    public MeetupReport(UUID meetupId, UUID reporterId, String reason) {
        this.meetupId = meetupId;
        this.reporterId = reporterId;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getMeetupId() { return meetupId; }
    public UUID getReporterId() { return reporterId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
