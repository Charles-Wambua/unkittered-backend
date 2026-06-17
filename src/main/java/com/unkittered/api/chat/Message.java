package com.unkittered.api.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A single chat message belonging to a {@code Match} (conversation). */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Set when the recipient has read the message; null while unread. */
    @Column(name = "read_at")
    private Instant readAt;

    protected Message() { }

    public Message(UUID matchId, UUID senderId, String body) {
        this.matchId = matchId;
        this.senderId = senderId;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public UUID getSenderId() { return senderId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
