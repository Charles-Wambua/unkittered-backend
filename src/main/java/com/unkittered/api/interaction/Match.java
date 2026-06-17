package com.unkittered.api.interaction;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A mutual like. The pair is stored canonically with userLow &lt; userHigh. */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_low", nullable = false)
    private UUID userLow;

    @Column(name = "user_high", nullable = false)
    private UUID userHigh;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Match() { }

    private Match(UUID userLow, UUID userHigh) {
        this.userLow = userLow;
        this.userHigh = userHigh;
    }

    /** Builds a match with the canonical ordering regardless of argument order. */
    public static Match of(UUID a, UUID b) {
        return new Match(low(a, b), high(a, b));
    }

    /** Lower of the pair under PostgreSQL's UUID ordering. */
    public static UUID low(UUID a, UUID b) {
        return compareUnsigned(a, b) <= 0 ? a : b;
    }

    /** Higher of the pair under PostgreSQL's UUID ordering. */
    public static UUID high(UUID a, UUID b) {
        return compareUnsigned(a, b) <= 0 ? b : a;
    }

    /**
     * PostgreSQL compares {@code uuid} values bytewise as unsigned, but
     * {@link UUID#compareTo} compares the bit halves as <em>signed</em> longs.
     * They disagree whenever a UUID's top bit is set, which would break the
     * {@code chk_match_order} (user_low &lt; user_high) constraint. Compare the
     * way Postgres does so the canonical pair always satisfies the check.
     */
    private static int compareUnsigned(UUID a, UUID b) {
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return cmp != 0 ? cmp
                : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    public UUID getId() { return id; }
    public UUID getUserLow() { return userLow; }
    public UUID getUserHigh() { return userHigh; }
    public Instant getCreatedAt() { return createdAt; }
}
