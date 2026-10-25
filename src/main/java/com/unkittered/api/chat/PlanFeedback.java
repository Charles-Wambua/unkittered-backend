package com.unkittered.api.chat;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One person's private verdict on a date that happened ("good" | "bad"). */
@Entity
@Table(name = "plan_feedback")
@IdClass(PlanFeedback.Key.class)
public class PlanFeedback {

    public static class Key implements Serializable {
        private UUID userId;
        private String planId;

        public Key() { }

        public Key(UUID userId, String planId) {
            this.userId = userId;
            this.planId = planId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(planId, k.planId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, planId); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "plan_id")
    private String planId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private String rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PlanFeedback() { }

    public PlanFeedback(UUID userId, UUID matchId, String planId, String rating) {
        this.userId = userId;
        this.matchId = matchId;
        this.planId = planId;
        this.rating = rating;
    }

    public String getPlanId() { return planId; }
    public String getRating() { return rating; }
    public void setRating(String rating) {
        this.rating = rating;
        this.createdAt = Instant.now();
    }
}
