package com.unkittered.api.chat;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** "Delete chat": hides a conversation's history before {@code clearedAt}, for one person. */
@Entity
@Table(name = "chat_clears")
@IdClass(ChatClear.Key.class)
public class ChatClear {

    public static class Key implements Serializable {
        private UUID userId;
        private UUID matchId;

        public Key() { }

        public Key(UUID userId, UUID matchId) {
            this.userId = userId;
            this.matchId = matchId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(matchId, k.matchId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, matchId); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "cleared_at", nullable = false)
    private Instant clearedAt;

    protected ChatClear() { }

    public ChatClear(UUID userId, UUID matchId, Instant clearedAt) {
        this.userId = userId;
        this.matchId = matchId;
        this.clearedAt = clearedAt;
    }

    public Instant getClearedAt() { return clearedAt; }
    public void setClearedAt(Instant v) { this.clearedAt = v; }
}
