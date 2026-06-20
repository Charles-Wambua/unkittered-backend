package com.unkittered.api.stories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    /**
     * Live (non-expired) story frames the viewer is allowed to see: their OWN,
     * plus only people they've MATCHED with. Grouped by user, oldest frame
     * first within a user. (A block removes the match, so blocked users fall
     * out automatically.)
     */
    @Query(value = """
        SELECT s.*
        FROM stories s
        WHERE s.created_at >= :cutoff
          AND (
            s.user_id = :viewerId
            OR s.user_id IN (
              SELECT m.user_high FROM matches m WHERE m.user_low  = :viewerId
              UNION
              SELECT m.user_low  FROM matches m WHERE m.user_high = :viewerId
            )
          )
        ORDER BY s.user_id, s.created_at ASC
        """, nativeQuery = true)
    List<Story> findLiveFor(@Param("viewerId") UUID viewerId, @Param("cutoff") Instant cutoff);

    /** Remove all of a user's story frames. */
    void deleteByUserId(UUID userId);
}
