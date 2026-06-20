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

    protected MeetupRsvp() { }

    public MeetupRsvp(UUID meetupId, UUID userId) {
        this.meetupId = meetupId;
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
}
