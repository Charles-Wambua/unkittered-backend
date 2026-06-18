package com.unkittered.api.stories;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A single ephemeral story frame. Expires 24h after {@link #createdAt}. */
@Entity
@Table(name = "stories")
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Image URL for a photo frame; null for a text-only frame. */
    @Column(name = "image_url")
    private String imageUrl;

    /** Caption (photo frame) or the text of a text-only frame; null otherwise. */
    @Column(name = "body")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Story() { }

    public Story(UUID userId, String imageUrl, String body) {
        this.userId = userId;
        this.imageUrl = imageUrl;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getImageUrl() { return imageUrl; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
