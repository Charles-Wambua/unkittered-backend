package com.unkittered.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    /** Count of verified profiles (for the admin dashboard). */
    long countByVerifiedTrue();

    /** Bump the user's last-active timestamp without loading the entity. */
    @Modifying
    @Query("UPDATE Profile p SET p.lastActiveAt = :now WHERE p.userId = :id")
    int touchLastActive(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Candidate deck for discover: everyone who has finished onboarding (no
     * half-made profiles without a photo or pledge), except the viewer, anyone
     * the viewer has already liked/super-liked/passed on, and anyone involved
     * in a block with the viewer in either direction.
     */
    @Query("""
        SELECT p FROM Profile p
        WHERE p.userId <> :viewerId
          AND p.userId IN (
              SELECT u.id FROM User u WHERE u.onboardingComplete = true
          )
          AND p.userId NOT IN (
              SELECT l.likeeId FROM Like l WHERE l.likerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT pa.passeeId FROM Pass pa WHERE pa.passerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT b.blockedId FROM Block b WHERE b.blockerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT b.blockerId FROM Block b WHERE b.blockedId = :viewerId
          )
          AND (
              p.incognito = false
              OR p.userId IN (
                  SELECT l2.likerId FROM Like l2 WHERE l2.likeeId = :viewerId
              )
          )
        ORDER BY
          CASE WHEN p.boostedUntil IS NOT NULL AND p.boostedUntil > :now THEN 0 ELSE 1 END,
          p.lastActiveAt DESC
        """)
    List<Profile> findDeckFor(@Param("viewerId") UUID viewerId, @Param("now") Instant now);

    /** Which of these profiles are paused (applied to cached decks so pausing is immediate). */
    @Query("SELECT p.userId FROM Profile p WHERE p.paused = true AND p.userId IN :ids")
    List<UUID> findPausedAmong(@Param("ids") Collection<UUID> ids);

    /**
     * Profiles whose owners have liked the viewer and are still waiting on an
     * answer: anyone the viewer already wagged back (a match) or passed on drops
     * out, as do blocks in either direction.
     */
    @Query("""
        SELECT p FROM Profile p
        WHERE p.userId IN (
            SELECT l.likerId FROM Like l WHERE l.likeeId = :viewerId
        )
          AND p.userId NOT IN (
              SELECT l2.likeeId FROM Like l2 WHERE l2.likerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT ps.passeeId FROM Pass ps WHERE ps.passerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT b.blockedId FROM Block b WHERE b.blockerId = :viewerId
          )
          AND p.userId NOT IN (
              SELECT b.blockerId FROM Block b WHERE b.blockedId = :viewerId
          )
        ORDER BY p.lastActiveAt DESC
        """)
    List<Profile> findWhoLiked(@Param("viewerId") UUID viewerId);
}
