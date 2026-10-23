package com.unkittered.api.chat;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DateConciergeTest {

    // A Wednesday afternoon in Nairobi.
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 21, 15, 0, 0, 0, DateConcierge.ZONE);

    @Test
    void sharedInterestsComeFirstAndPetsMeanAPicnic() {
        var plans = DateConcierge.suggest(new DateConcierge.Input(
                "Amara", "Kilimani", List.of("Hiking", "Reading"), List.of("Hiking", "Coffee"), true), NOW, 3);
        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).emoji()).isEqualTo("🐾");
        assertThat(plans.get(1).title()).contains("trail");
        assertThat(plans).allSatisfy(p -> assertThat(p.area()).isEqualTo("Kilimani"));
    }

    @Test
    void theDayInTheLabelIsTheDayItIsBooked() {
        var plans = DateConcierge.suggest(new DateConcierge.Input(
                "Amara", "", List.of(), List.of("Cooking", "Books", "Live music"), false), NOW, 3);
        for (var p : plans) {
            String day = p.startsAt().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            assertThat(p.when()).startsWith(day);
            assertThat(p.startsAt()).isAfter(NOW);
        }
    }

    @Test
    void nextSkipsAPlanThatIsTooSoon() {
        // Wednesday 15:00 → "Wednesday 16:00" is within 3 hours, so next week.
        var at = DateConcierge.next(DayOfWeek.WEDNESDAY, 16, NOW);
        assertThat(at.toLocalDate()).isEqualTo(NOW.toLocalDate().plusWeeks(1));
    }

    @Test
    void noInterestsStillGivesFallbackIdeas() {
        var plans = DateConcierge.suggest(new DateConcierge.Input("Amara", null, null, null, false), NOW, 3);
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0).area()).isEqualTo("somewhere central");
    }
}
