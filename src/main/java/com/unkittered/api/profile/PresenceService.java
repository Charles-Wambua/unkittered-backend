package com.unkittered.api.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks user activity by bumping {@code profiles.last_active_at}. Online status
 * is then derived from how recently that timestamp moved (see {@link ProfileMapper}).
 *
 * Writes are throttled in-memory so a burst of requests from one user results in
 * at most one DB update per {@link #THROTTLE_MS}.
 */
@Service
public class PresenceService {

    private static final long THROTTLE_MS = 60_000; // at most one write/minute/user

    private final ProfileRepository profiles;
    private final Map<UUID, Long> lastWrite = new ConcurrentHashMap<>();

    public PresenceService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** Record that the user is active right now (best-effort, throttled). */
    @Transactional
    public void touch(UUID userId) {
        if (userId == null) return;
        long now = System.currentTimeMillis();
        Long prev = lastWrite.get(userId);
        if (prev != null && now - prev < THROTTLE_MS) return;
        lastWrite.put(userId, now);
        try {
            profiles.touchLastActive(userId, Instant.now());
        } catch (Exception ignored) {
            // Presence is best-effort; never fail a request because of it.
            lastWrite.remove(userId);
        }
    }
}
