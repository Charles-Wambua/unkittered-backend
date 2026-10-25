package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PackMessageRepository extends JpaRepository<PackMessage, UUID> {
    List<PackMessage> findByMeetupIdOrderByCreatedAtAsc(UUID meetupId);

    long countByMeetupIdAndSenderIdNot(UUID meetupId, UUID senderId);

    long countByMeetupIdAndSenderIdNotAndCreatedAtAfter(UUID meetupId, UUID senderId, Instant after);
}
