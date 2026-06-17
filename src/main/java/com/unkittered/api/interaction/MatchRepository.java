package com.unkittered.api.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    boolean existsByUserLowAndUserHigh(UUID userLow, UUID userHigh);

    Optional<Match> findByUserLowAndUserHigh(UUID userLow, UUID userHigh);

    /** Every match the given user is part of, newest first. */
    @Query("""
        SELECT m FROM Match m
        WHERE m.userLow = :userId OR m.userHigh = :userId
        ORDER BY m.createdAt DESC
        """)
    List<Match> findAllForUser(@Param("userId") UUID userId);
}
