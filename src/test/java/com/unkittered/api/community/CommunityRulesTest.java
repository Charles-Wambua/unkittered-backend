package com.unkittered.api.community;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityRulesTest {

    private static ShenronSeason on(int month, int day) {
        return ShenronSeason.of(LocalDate.of(2026, month, day));
    }

    @Test
    void shenronDressesForEachHolidayWindowAndNoLonger() {
        assertThat(on(12, 31)).isEqualTo(ShenronSeason.NEW_YEAR);
        assertThat(on(1, 2)).isEqualTo(ShenronSeason.NEW_YEAR);
        assertThat(on(1, 3)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(2, 9)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(2, 10)).isEqualTo(ShenronSeason.VALENTINES);
        assertThat(on(2, 14)).isEqualTo(ShenronSeason.VALENTINES);
        assertThat(on(2, 15)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(5, 30)).isEqualTo(ShenronSeason.MADARAKA);
        assertThat(on(6, 1)).isEqualTo(ShenronSeason.MADARAKA);
        assertThat(on(6, 2)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(10, 18)).isEqualTo(ShenronSeason.MASHUJAA);
        assertThat(on(10, 20)).isEqualTo(ShenronSeason.MASHUJAA);
        assertThat(on(10, 25)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(10, 26)).isEqualTo(ShenronSeason.HALLOWEEN);
        assertThat(on(10, 31)).isEqualTo(ShenronSeason.HALLOWEEN);
        assertThat(on(12, 10)).isEqualTo(ShenronSeason.JAMHURI);
        assertThat(on(12, 12)).isEqualTo(ShenronSeason.JAMHURI);
        assertThat(on(12, 13)).isEqualTo(ShenronSeason.NONE);
        assertThat(on(12, 18)).isEqualTo(ShenronSeason.CHRISTMAS);
        assertThat(on(12, 27)).isEqualTo(ShenronSeason.CHRISTMAS);
        assertThat(on(12, 28)).isEqualTo(ShenronSeason.NONE);
    }

    @Test
    void seasonalPhotosLiveUnderThePublicSite() {
        assertThat(ShenronSeason.NONE.photoPath()).isEqualTo("/site/official/shenron.png");
        assertThat(ShenronSeason.NEW_YEAR.photoPath()).isEqualTo("/site/official/shenron-new-year.png");
    }

    @Test
    void nominationWeeksStartOnMonday() {
        // 10 Nov 2026 is a Tuesday.
        assertThat(PetOfWeekService.weekOf(LocalDate.of(2026, 11, 10))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(PetOfWeekService.weekOf(LocalDate.of(2026, 11, 9))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(PetOfWeekService.weekOf(LocalDate.of(2026, 11, 15))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void winnerStoryNamesThePetAndCongratulatesTheOwner() {
        assertThat(PetOfWeekService.storyText("Simba", "Daniel", 3))
                .isEqualTo("🏆 Pet of the week: Simba!\nNominated by 3 members. Congrats, Daniel!");
        assertThat(PetOfWeekService.storyText("Mochi", null, 1))
                .isEqualTo("🏆 Pet of the week: Mochi!\nNominated by a fellow member.");
    }

    @Test
    void petOfTheWeekLeadsTheBadgesAndNotesAnnounceThem() {
        assertThat(Badges.rank(Badges.PET_OF_WEEK)).isLessThan(Badges.rank(Badges.EARLY_TESTER));
        assertThat(Badges.noteFor(Badges.find(Badges.VERIFIED).orElseThrow())).startsWith("🏅 New badge: ✅ Verified");
    }
}
