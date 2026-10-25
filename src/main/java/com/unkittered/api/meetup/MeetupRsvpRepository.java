package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetupRsvpRepository extends JpaRepository<MeetupRsvp, UUID> {
    long countByMeetupId(UUID meetupId);
    java.util.Optional<MeetupRsvp> findByMeetupIdAndUserId(UUID meetupId, UUID userId);

    long countByMeetupIdAndWithPetTrue(UUID meetupId);

    boolean existsByMeetupIdAndUserId(UUID meetupId, UUID userId);
    List<MeetupRsvp> findByMeetupId(UUID meetupId);
    List<MeetupRsvp> findByUserId(UUID userId);
    List<MeetupRsvp> findByMeetupIdAndReminded24hFalse(UUID meetupId);
    List<MeetupRsvp> findByMeetupIdAndReminded1hFalse(UUID meetupId);
    void deleteByMeetupIdAndUserId(UUID meetupId, UUID userId);
}
