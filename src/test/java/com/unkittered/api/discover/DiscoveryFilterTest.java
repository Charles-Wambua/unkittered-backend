package com.unkittered.api.discover;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryFilterTest {

    private static DiscoveryFilter.Candidate person(int age, double km, boolean verified, boolean sterilised,
                                                    List<String> species, List<String> interests) {
        return new DiscoveryFilter.Candidate(age, km, verified, sterilised, species, interests);
    }

    private static final DiscoveryFilter.Candidate NALEDI =
            person(31, 4, true, false, List.of("dog"), List.of("Hiking", "Travel"));

    @Test
    void noPrefsShowsEveryone() {
        assertThat(DiscoveryFilter.matches(null, NALEDI)).isTrue();
    }

    @Test
    void ageRangeIsInclusive() {
        DiscoveryPrefs p = new DiscoveryPrefs(UUID.randomUUID());
        p.setAgeMin(31);
        p.setAgeMax(35);
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isTrue();
        p.setAgeMin(32);
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isFalse();
    }

    @Test
    void distanceIgnoresPeopleWhoHideIt() {
        DiscoveryPrefs p = new DiscoveryPrefs(UUID.randomUUID());
        p.setMaxDistanceKm(3);
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isFalse();
        assertThat(DiscoveryFilter.matches(p, person(31, -1, true, false, List.of(), List.of()))).isTrue();
    }

    @Test
    void badgeFilters() {
        DiscoveryPrefs p = new DiscoveryPrefs(UUID.randomUUID());
        p.setSterilisedOnly(true);
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isFalse();
        assertThat(DiscoveryFilter.matches(p, person(31, 4, false, true, List.of(), List.of()))).isTrue();
        p.setSterilisedOnly(false);
        p.setVerifiedOnly(true);
        assertThat(DiscoveryFilter.matches(p, person(31, 4, false, true, List.of(), List.of()))).isFalse();
    }

    @Test
    void speciesAndInterestsNeedAnOverlap() {
        DiscoveryPrefs p = new DiscoveryPrefs(UUID.randomUUID());
        p.setSpecies(Set.of("cat"));
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isFalse();
        p.setSpecies(Set.of("cat", "dog"));
        p.setInterests(Set.of("Chess"));
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isFalse();
        p.setInterests(Set.of("Chess", "Hiking"));
        assertThat(DiscoveryFilter.matches(p, NALEDI)).isTrue();
    }
}
