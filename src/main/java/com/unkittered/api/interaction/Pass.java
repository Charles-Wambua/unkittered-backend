package com.unkittered.api.interaction;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passes")
public class Pass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "passer_id", nullable = false)
    private UUID passerId;

    @Column(name = "passee_id", nullable = false)
    private UUID passeeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Pass() { }

    public Pass(UUID passerId, UUID passeeId) {
        this.passerId = passerId;
        this.passeeId = passeeId;
    }

    public UUID getId() { return id; }
    public UUID getPasserId() { return passerId; }
    public UUID getPasseeId() { return passeeId; }
    public Instant getCreatedAt() { return createdAt; }
}
