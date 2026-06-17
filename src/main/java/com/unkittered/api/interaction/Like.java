package com.unkittered.api.interaction;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "likes")
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "liker_id", nullable = false)
    private UUID likerId;

    @Column(name = "likee_id", nullable = false)
    private UUID likeeId;

    @Column(name = "super_like", nullable = false)
    private boolean superLike = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Like() { }

    public Like(UUID likerId, UUID likeeId, boolean superLike) {
        this.likerId = likerId;
        this.likeeId = likeeId;
        this.superLike = superLike;
    }

    public UUID getId() { return id; }
    public UUID getLikerId() { return likerId; }
    public UUID getLikeeId() { return likeeId; }
    public boolean isSuperLike() { return superLike; }
    public Instant getCreatedAt() { return createdAt; }
}
