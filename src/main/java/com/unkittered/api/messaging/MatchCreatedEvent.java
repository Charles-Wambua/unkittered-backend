package com.unkittered.api.messaging;

import java.time.Instant;
import java.util.UUID;

/** Published to SQS when two users mutually like each other. */
public record MatchCreatedEvent(
        UUID matchId,
        UUID userA,
        UUID userB,
        boolean superLike,
        Instant createdAt) {
}
