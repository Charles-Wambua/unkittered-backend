package com.unkittered.api.meetup;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PackTimelineTest {

    private static final Instant START = Instant.parse("2026-11-07T06:00:00Z");

    /** A two-hour pack starting at {@link #START}. */
    private static Meetup pack() {
        Meetup m = new Meetup(UUID.randomUUID(), "Karura walk", "Walk", "Gigiri", "Karura", START, 10, "");
        m.setDurationMinutes(120);
        return m;
    }

    private static Instant at(Duration fromStart) {
        return START.plus(fromStart);
    }

    @Test
    void statusFollowsStartAndEnd() {
        assertThat(PackTimeline.status(pack(), at(Duration.ofMinutes(-1)))).isEqualTo("upcoming");
        assertThat(PackTimeline.status(pack(), START)).isEqualTo("live");
        assertThat(PackTimeline.status(pack(), at(Duration.ofMinutes(119)))).isEqualTo("live");
        assertThat(PackTimeline.status(pack(), at(Duration.ofMinutes(120)))).isEqualTo("ended");
    }

    @Test
    void rsvpsCloseWhenThePackStarts() {
        assertThat(PackTimeline.acceptsRsvps(pack(), at(Duration.ofSeconds(-1)))).isTrue();
        assertThat(PackTimeline.acceptsRsvps(pack(), START)).isFalse();
        assertThat(PackTimeline.acceptsRsvps(pack(), at(Duration.ofDays(2)))).isFalse();
    }

    @Test
    void chatOpensADayBeforeAndClosesADayAfterTheEnd() {
        assertThat(PackTimeline.chatState(pack(), at(Duration.ofHours(-25)))).isEqualTo("locked");
        assertThat(PackTimeline.chatState(pack(), at(Duration.ofHours(-24)))).isEqualTo("open");
        assertThat(PackTimeline.chatState(pack(), at(Duration.ofHours(25)))).isEqualTo("open");
        // Ends at +2h, so it closes at +26h.
        assertThat(PackTimeline.chatState(pack(), at(Duration.ofHours(26)))).isEqualTo("closed");
        assertThat(PackTimeline.chatClosesAt(pack())).isEqualTo(at(Duration.ofHours(26)));
    }
}
