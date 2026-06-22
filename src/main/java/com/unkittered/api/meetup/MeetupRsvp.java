package com.unkittered.api.meetup;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A user's RSVP to a meetup. */
@Entity
@Table(name = "meetup_rsvps")
public class MeetupRsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meetup_id", nullable = false)
    private UUID meetupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reminded_24h", nullable = false)
    private boolean reminded24h = false;

    @Column(name = "reminded_1h", nullable = false)
    private boolean reminded1h = false;

    protected MeetupRsvp() { }

    public MeetupRsvp(UUID meetupId, UUID userId) {
        this.meetupId = meetupId;
        this.userId = userId;
    }

    public UUID getMeetupId() { return meetupId; }
    public UUID getUserId() { return userId; }
    public boolean isReminded24h() { return reminded24h; }
    public void setReminded24h(boolean v) { this.reminded24h = v; }
    public boolean isReminded1h() { return reminded1h; }
    public void setReminded1h(boolean v) { this.reminded1h = v; }
}
