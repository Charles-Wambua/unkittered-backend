package com.unkittered.api.reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReelRepository extends JpaRepository<Reel, UUID> {

    /**
     * Feed of other people's reels, newest first. Profiles that are hidden
     * (incognito) or involved in a block with the viewer in either direction are
     * excluded; only the latest reel per user is kept (DISTINCT ON user_id).
     */
    @Query(value = """
        SELECT DISTINCT ON (r.user_id) r.*
        FROM reels r
        JOIN profiles p ON p.user_id = r.user_id
        WHERE r.user_id <> :viewerId
          AND p.incognito = false
          AND r.user_id NOT IN (SELECT b.blocked_id FROM blocks b WHERE b.blocker_id = :viewerId)
          AND r.user_id NOT IN (SELECT b.blocker_id FROM blocks b WHERE b.blocked_id = :viewerId)
        ORDER BY r.user_id, r.created_at DESC
        """, nativeQuery = true)
    List<Reel> findFeedFor(@Param("viewerId") UUID viewerId);

    /** The user's current (latest) reel, if any. */
    java.util.Optional<Reel> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Remove a user's existing reels so an upload replaces the active one. */
    void deleteByUserId(UUID userId);
}
