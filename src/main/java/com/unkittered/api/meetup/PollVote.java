package com.unkittered.api.meetup;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A member's vote in a pack chat poll (one per poll; voting again changes it). */
@Entity
@Table(name = "meetup_poll_votes")
@IdClass(PollVote.Key.class)
public class PollVote {

    public static class Key implements Serializable {
        private UUID messageId;
        private UUID userId;

        public Key() { }

        public Key(UUID messageId, UUID userId) {
            this.messageId = messageId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(messageId, k.messageId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() { return Objects.hash(messageId, userId); }
    }

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "option_index", nullable = false)
    private int option;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PollVote() { }

    public PollVote(UUID messageId, UUID userId, int option) {
        this.messageId = messageId;
        this.userId = userId;
        this.option = option;
    }

    public UUID getMessageId() { return messageId; }
    public UUID getUserId() { return userId; }
    public int getOption() { return option; }
    public void setOption(int option) { this.option = option; }
}
