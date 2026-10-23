package com.unkittered.api.verification;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A verified member confirming they know [userId]. */
@Entity
@Table(name = "vouches")
@IdClass(Vouch.Key.class)
public class Vouch {

    public record Key(UUID userId, UUID voucherId) implements Serializable {
        public Key() { this(null, null); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "voucher_id")
    private UUID voucherId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vouch() { }

    public Vouch(UUID userId, UUID voucherId, Instant at) {
        this.userId = userId;
        this.voucherId = voucherId;
        this.createdAt = at;
    }
}
