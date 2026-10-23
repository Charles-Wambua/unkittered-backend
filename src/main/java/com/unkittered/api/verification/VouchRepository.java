package com.unkittered.api.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VouchRepository extends JpaRepository<Vouch, Vouch.Key> {
    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndVoucherId(UUID userId, UUID voucherId);
}
