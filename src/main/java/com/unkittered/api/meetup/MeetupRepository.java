package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MeetupRepository extends JpaRepository<Meetup, UUID> {
    /** Soonest first (nulls last is fine for MVP). */
    List<Meetup> findAllByOrderByStartsAtAsc();

    /** Meetups starting within a window — used by the reminder scheduler. */
    List<Meetup> findByStartsAtBetween(Instant from, Instant to);
}
