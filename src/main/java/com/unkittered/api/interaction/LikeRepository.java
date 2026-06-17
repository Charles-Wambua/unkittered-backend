package com.unkittered.api.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, UUID> {
    boolean existsByLikerIdAndLikeeId(UUID likerId, UUID likeeId);
    long countByLikerIdAndCreatedAtAfter(UUID likerId, java.time.Instant after);
}
