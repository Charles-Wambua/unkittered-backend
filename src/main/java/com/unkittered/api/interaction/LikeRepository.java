package com.unkittered.api.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, UUID> {
    boolean existsByLikerIdAndLikeeId(UUID likerId, UUID likeeId);
    java.util.Optional<Like> findByLikerIdAndLikeeId(UUID likerId, UUID likeeId);
    long countByLikerIdAndCreatedAtAfter(UUID likerId, java.time.Instant after);

    /**
     * Forgets the wags between two people (both directions), so a block or an
     * unmatch can't be undone by one of them wagging again.
     */
    @Modifying
    @Query(value = "DELETE FROM likes WHERE (liker_id = :a AND likee_id = :b) OR (liker_id = :b AND likee_id = :a)",
            nativeQuery = true)
    int deleteBetween(@Param("a") UUID a, @Param("b") UUID b);
}
