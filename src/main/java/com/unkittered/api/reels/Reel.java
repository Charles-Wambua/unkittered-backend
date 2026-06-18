package com.unkittered.api.reels;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A video profile uploaded by a user. The most recent per user is their active reel. */
@Entity
@Table(name = "reels")
public class Reel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "video_url", nullable = false)
    private String videoUrl;

    @Column(name = "poster_url", nullable = false)
    private String posterUrl = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Reel() { }

    public Reel(UUID userId, String videoUrl, String posterUrl) {
        this.userId = userId;
        this.videoUrl = videoUrl;
        this.posterUrl = posterUrl == null ? "" : posterUrl;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getVideoUrl() { return videoUrl; }
    public String getPosterUrl() { return posterUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
