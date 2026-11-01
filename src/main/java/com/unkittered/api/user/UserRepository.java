package com.unkittered.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByIdAndSuspendedFalse(UUID id);

    /** Which of these users are suspended. */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.suspended = true")
    List<UUID> findSuspendedIds(@Param("ids") Collection<UUID> ids);
    long countBySubscriptionTierNot(String tier);
    long countBySubscriptionTier(String tier);
    long countByCreatedAtAfter(Instant t);
    long countByCreatedAtBetween(Instant from, Instant to);
    List<User> findTop20BySubscriptionTierNotOrderByCreatedAtAsc(String tier);
}
