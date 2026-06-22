package com.unkittered.api.meetup;

import com.unkittered.api.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Pushes trip/meetup reminders so RSVPs don't forget. Runs every 15 minutes and
 * sends two waves — a day-before nudge and a starting-soon nudge — each tracked
 * per-RSVP so it never double-sends. (The Flutter client also schedules an
 * on-device local notification at RSVP time, which fires even fully offline.)
 */
@Component
public class MeetupReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetupReminderScheduler.class);

    private final MeetupRepository meetups;
    private final MeetupRsvpRepository rsvps;
    private final PushService push;

    public MeetupReminderScheduler(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                                   PushService push) {
        this.meetups = meetups;
        this.rsvps = rsvps;
        this.push = push;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000L)  // every 15 minutes
    @Transactional
    public void sweep() {
        if (!push.isEnabled()) return;
        Instant now = Instant.now();

        // Starting-soon (≤ 1h away): the urgent nudge.
        for (Meetup m : meetups.findByStartsAtBetween(now, now.plus(Duration.ofHours(1)))) {
            List<MeetupRsvp> pending = rsvps.findByMeetupIdAndReminded1hFalse(m.getId());
            if (pending.isEmpty()) continue;
            push.sendToUsers(pending.stream().map(MeetupRsvp::getUserId).toList(),
                    "Starting soon ⏰",
                    "“" + m.getTitle() + "” starts within the hour at " + venueOrArea(m) + ".",
                    Map.of("type", "meetup", "meetupId", m.getId().toString()));
            pending.forEach(r -> r.setReminded1h(true));
            rsvps.saveAll(pending);
            log.info("Sent 1h reminder for meetup {} to {} RSVPs", m.getId(), pending.size());
        }

        // Day-before (1h–24h away): the heads-up.
        for (Meetup m : meetups.findByStartsAtBetween(now.plus(Duration.ofHours(1)),
                now.plus(Duration.ofHours(24)))) {
            List<MeetupRsvp> pending = rsvps.findByMeetupIdAndReminded24hFalse(m.getId());
            if (pending.isEmpty()) continue;
            push.sendToUsers(pending.stream().map(MeetupRsvp::getUserId).toList(),
                    "Coming up 🐾",
                    "“" + m.getTitle() + "” is happening soon. Don't miss it!",
                    Map.of("type", "meetup", "meetupId", m.getId().toString()));
            pending.forEach(r -> r.setReminded24h(true));
            rsvps.saveAll(pending);
            log.info("Sent 24h reminder for meetup {} to {} RSVPs", m.getId(), pending.size());
        }
    }

    private static String venueOrArea(Meetup m) {
        return m.getVenue() != null && !m.getVenue().isBlank() ? m.getVenue() : m.getArea();
    }
}
