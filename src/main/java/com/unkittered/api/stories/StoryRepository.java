package com.unkittered.api.stories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    /**
     * Live (non-expired) story frames for the viewer's row: their own plus
     * other people's, excluding incognito profiles and anyone in a block with
     * the viewer either way. Grouped by user, oldest frame first within a user.
     */
    @Query(value = """
        SELECT s.*
        FROM stories s
        JOIN profiles p ON p.user_id = s.user_id
        WHERE s.created_at >= :cutoff
          AND (p.incognito = false OR s.user_id = :viewerId)
          AND s.user_id NOT IN (SELECT b.blocked_id FROM blocks b WHERE b.blocker_id = :viewerId)
          AND s.user_id NOT IN (SELECT b.blocker_id FROM blocks b WHERE b.blocked_id = :viewerId)
        ORDER BY s.user_id, s.created_at ASC
        """, nativeQuery = true)
    List<Story> findLiveFor(@Param("viewerId") UUID viewerId, @Param("cutoff") Instant cutoff);

    /** Remove all of a user's story frames. */
    void deleteByUserId(UUID userId);
}
