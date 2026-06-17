package com.unkittered.api.safety;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** {@code blockerId} has blocked {@code blockedId}. Directional. */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Block() { }

    public Block(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public UUID getId() { return id; }
    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public Instant getCreatedAt() { return createdAt; }
}
