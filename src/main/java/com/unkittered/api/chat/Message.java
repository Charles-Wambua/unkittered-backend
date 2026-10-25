package com.unkittered.api.chat;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
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

    /** {@link RichMessages.Kind} wire value. */
    @Column(nullable = false)
    private String kind = "text";

    /** Structured data for non-text kinds; null for plain text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** The message this one replies to (swipe-to-reply), or null. */
    @Column(name = "reply_to_id")
    private UUID replyToId;

    /** Set when the recipient has read the message; null while unread. */
    @Column(name = "read_at")
    private Instant readAt;

    protected Message() { }

    public Message(UUID matchId, UUID senderId, String body) {
        this.matchId = matchId;
        this.senderId = senderId;
        this.body = body;
    }

    public Message(UUID matchId, UUID senderId, RichMessages.Prepared prepared) {
        this(matchId, senderId, prepared.body());
        this.kind = prepared.kind().wire();
        this.payload = prepared.payload();
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public UUID getSenderId() { return senderId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public String getKind() { return kind; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public UUID getReplyToId() { return replyToId; }
    public void setReplyToId(UUID replyToId) { this.replyToId = replyToId; }
}
