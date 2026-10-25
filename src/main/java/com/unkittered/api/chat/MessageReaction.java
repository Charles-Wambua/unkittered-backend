package com.unkittered.api.chat;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An emoji reaction on a 1:1 message. */
@Entity
@Table(name = "message_reactions")
@IdClass(MessageReaction.Key.class)
public class MessageReaction {

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

    @Column(nullable = false)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MessageReaction() { }

    public MessageReaction(UUID messageId, UUID userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }

    public UUID getMessageId() { return messageId; }
    public UUID getUserId() { return userId; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
}
