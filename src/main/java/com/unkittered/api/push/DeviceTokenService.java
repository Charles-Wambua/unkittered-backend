package com.unkittered.api.push;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Registers / removes a user's FCM device tokens. */
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository tokens;

    public DeviceTokenService(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Upsert: a token is globally unique, so re-registering moves it to this user. */
    @Transactional
    public void register(UUID userId, String token, String platform) {
        if (token == null || token.isBlank()) return;
        DeviceToken existing = tokens.findById(token).orElse(null);
        if (existing == null) {
            tokens.save(new DeviceToken(token, userId, platform));
        } else {
            existing.setUserId(userId);
            existing.setPlatform(platform == null ? existing.getPlatform() : platform);
            existing.setUpdatedAt(Instant.now());
            tokens.save(existing);
        }
    }

    @Transactional
    public void unregister(String token) {
        if (token != null && !token.isBlank()) {
            tokens.deleteByToken(token);
        }
    }
}
