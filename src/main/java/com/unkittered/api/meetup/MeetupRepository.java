package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MeetupRepository extends JpaRepository<Meetup, UUID> {
    /** Soonest first (nulls last is fine for MVP). */
    List<Meetup> findAllByOrderByStartsAtAsc();

    /**
     * Candidates for the board: anything that started less than a day ago
     * (packs last at most 24h), soonest first. The service drops ended ones.
     */
    List<Meetup> findByStartsAtAfterOrderByStartsAtAsc(Instant startedAfter);

    /** Meetups starting within a window — used by the reminder scheduler. */
    List<Meetup> findByStartsAtBetween(Instant from, Instant to);
}
