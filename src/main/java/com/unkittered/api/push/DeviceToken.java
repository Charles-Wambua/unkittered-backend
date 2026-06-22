package com.unkittered.api.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An FCM registration token for one of a user's devices. */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String platform = "unknown";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DeviceToken() { }

    public DeviceToken(String token, UUID userId, String platform) {
        this.token = token;
        this.userId = userId;
        this.platform = platform == null ? "unknown" : platform;
    }

    public String getToken() { return token; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
