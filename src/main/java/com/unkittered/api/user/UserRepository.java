package com.unkittered.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByIdAndSuspendedFalse(UUID id);
    long countBySubscriptionTierNot(String tier);
    long countBySubscriptionTier(String tier);
    long countByCreatedAtAfter(Instant t);
    long countByCreatedAtBetween(Instant from, Instant to);
    List<User> findTop20BySubscriptionTierNotOrderByCreatedAtAsc(String tier);
}
