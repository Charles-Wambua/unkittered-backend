package com.unkittered.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanFeedbackRepository extends JpaRepository<PlanFeedback, PlanFeedback.Key> {
    List<PlanFeedback> findByUserIdAndMatchId(UUID userId, UUID matchId);
}
