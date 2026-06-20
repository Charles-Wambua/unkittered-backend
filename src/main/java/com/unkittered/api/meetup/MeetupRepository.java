package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetupRepository extends JpaRepository<Meetup, UUID> {
    /** Soonest first (nulls last is fine for MVP). */
    List<Meetup> findAllByOrderByStartsAtAsc();
}
