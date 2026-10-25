package com.unkittered.api.meetup;

import java.time.Duration;
import java.time.Instant;

/**
 * When a pack is joinable and when its chat is open. Pure, so it's unit-tested.
 *
 * <ul>
 *   <li>RSVPs close when the pack starts; it drops off the board when it ends.</li>
 *   <li>The chat opens a day before it starts and turns read-only a day after
 *       it ends, so people can coordinate and then share photos/thanks.</li>
 * </ul>
 */
public final class PackTimeline {

    public static final Duration CHAT_OPENS_BEFORE = Duration.ofHours(24);
    public static final Duration CHAT_CLOSES_AFTER = Duration.ofHours(24);

    private PackTimeline() { }

    public static String status(Meetup m, Instant now) {
        if (now.isBefore(m.getStartsAt())) return "upcoming";
        return now.isBefore(m.getEndsAt()) ? "live" : "ended";
    }

    public static boolean acceptsRsvps(Meetup m, Instant now) {
        return now.isBefore(m.getStartsAt());
    }

    public static Instant chatOpensAt(Meetup m) {
        return m.getStartsAt().minus(CHAT_OPENS_BEFORE);
    }

    public static Instant chatClosesAt(Meetup m) {
        return m.getEndsAt().plus(CHAT_CLOSES_AFTER);
    }

    /** locked (too early) | open | closed (read-only). */
    public static String chatState(Meetup m, Instant now) {
        if (now.isBefore(chatOpensAt(m))) return "locked";
        return now.isBefore(chatClosesAt(m)) ? "open" : "closed";
    }
}
