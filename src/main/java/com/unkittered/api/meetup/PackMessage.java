package com.unkittered.api.meetup;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A message in a pack's group chat. */
@Entity
@Table(name = "meetup_messages")
public class PackMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meetup_id", nullable = false)
    private UUID meetupId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private String body;

    /** The message this one replies to, or null. */
    @Column(name = "reply_to_id")
    private UUID replyToId;

    /** text | arrival | meeting_point. */
    @Column(nullable = false)
    private String kind = PackChatService.TEXT;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    /** Polls: {"options": [...], "closed": false}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PackMessage() { }

    public PackMessage(UUID meetupId, UUID senderId, String body, UUID replyToId) {
        this.meetupId = meetupId;
        this.senderId = senderId;
        this.body = body;
        this.replyToId = replyToId;
    }

    public UUID getId() { return id; }
    public UUID getMeetupId() { return meetupId; }
    public UUID getSenderId() { return senderId; }
    public String getBody() { return body; }
    public UUID getReplyToId() { return replyToId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getKind() { return kind; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant getEditedAt() { return editedAt; }
    public Map<String, Object> getPayload() { return payload; }

    void edit(String text) {
        body = text;
        editedAt = Instant.now();
    }

    PackMessage payload(Map<String, Object> p) {
        this.payload = p;
        return this;
    }

    PackMessage kind(String k) {
        this.kind = k;
        return this;
    }

    /** Deleted for everyone: the text goes, the place in the thread stays. */
    void delete() {
        deletedAt = Instant.now();
        body = "";
    }
}
