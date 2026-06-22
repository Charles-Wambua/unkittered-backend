package com.unkittered.api.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByToken(String token);

    /** All tokens for a set of users — used to fan a push out to many recipients. */
    List<DeviceToken> findByUserIdIn(List<UUID> userIds);
}
